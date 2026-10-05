package com.watcher.scheduler;

import com.watcher.config.AppConfig;
import com.watcher.security.SecurityAuditor;
import com.watcher.storage.CloudRetentionCleaner;
import com.watcher.storage.LocalSpoolManager;
import com.watcher.storage.S3Uploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated background service that periodically purges expired captures
 * older than the configured retention policy (default: 30 days) from both
 * local spool storage and S3 cloud storage.
 */
public class RetentionCleanerService {
    private static final Logger logger = LogManager.getLogger(RetentionCleanerService.class);

    private final AppConfig config;
    private final LocalSpoolManager spoolManager;
    private volatile S3Uploader uploader;
    private final SecurityAuditor auditor;

    private ScheduledExecutorService scheduler;
    private volatile boolean isRunning = false;

    public RetentionCleanerService(AppConfig config,
                                  LocalSpoolManager spoolManager,
                                  S3Uploader uploader,
                                  SecurityAuditor auditor) {
        this.config = config;
        this.spoolManager = spoolManager;
        this.uploader = uploader;
        this.auditor = auditor;
    }

    public synchronized void start() {
        // Run after 1 minute warmup, then repeat once every 24 hours (1440 minutes)
        start(1, 1440, TimeUnit.MINUTES);
    }

    public synchronized void start(long initialDelay, long period, TimeUnit unit) {
        if (isRunning) {
            logger.warn("RetentionCleanerService is already running.");
            return;
        }

        ThreadFactory threadFactory = runnable -> {
            Thread t = new Thread(runnable, "retention-cleaner");
            t.setDaemon(true);
            return t;
        };

        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        this.scheduler.scheduleWithFixedDelay(this::runCleanupCycle, initialDelay, period, unit);
        this.isRunning = true;
        logger.info("RetentionCleanerService started. Initial delay: {} {}, period: {} {}",
                initialDelay, unit, period, unit);
    }

    public synchronized void stop() {
        if (!isRunning || scheduler == null) {
            return;
        }

        logger.info("Stopping RetentionCleanerService...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            isRunning = false;
            logger.info("RetentionCleanerService stopped.");
        }
    }

    public void runCleanupCycle() {
        int retentionDays = config.getRetentionDays();
        if (retentionDays <= 0) {
            logger.info("Retention days configured as <= 0. Skipping retention purge cycle.");
            return;
        }

        logger.info("Executing retention cleanup cycle (Retention: {} days)...", retentionDays);

        // 1. Purge local spool storage
        if (spoolManager != null) {
            try {
                int localPurged = spoolManager.purgeOlderThan(retentionDays);
                if (localPurged > 0) {
                    logger.info("Local retention cleanup finished. Purged {} expired files.", localPurged);
                    if (auditor != null) {
                        auditor.recordEvent("LOCAL_RETENTION_PURGE",
                                "Purged " + localPurged + " local captures older than " + retentionDays + " days.");
                    }
                } else {
                    logger.debug("Local retention cleanup: no expired captures found.");
                }
            } catch (Exception e) {
                logger.error("Error during local retention cleanup", e);
                if (auditor != null) {
                    auditor.recordEvent("RETENTION_ERROR", "Local retention cleanup failed: " + e.getMessage());
                }
            }
        }

        // 2. Purge S3 cloud folders
        if (uploader != null && uploader.getS3Client() != null) {
            try {
                CloudRetentionCleaner cloudCleaner = new CloudRetentionCleaner(uploader.getS3Client(), uploader.getBucketName());
                int cloudPurged = cloudCleaner.cleanOldFolders(retentionDays);
                if (cloudPurged > 0) {
                    logger.info("Cloud retention cleanup finished. Purged {} expired folders from S3.", cloudPurged);
                    if (auditor != null) {
                        auditor.recordEvent("CLOUD_RETENTION_PURGE",
                                "Purged " + cloudPurged + " expired cloud folders older than " + retentionDays + " days from S3.");
                    }
                } else {
                    logger.debug("Cloud retention cleanup: no expired cloud folders found.");
                }
            } catch (Exception e) {
                logger.error("Error during S3 cloud retention cleanup", e);
                if (auditor != null) {
                    auditor.recordEvent("RETENTION_ERROR", "S3 cloud retention cleanup failed: " + e.getMessage());
                }
            }
        } else {
            logger.debug("S3 uploader unavailable. Skipping cloud retention cleanup.");
        }
    }

    public void setUploader(S3Uploader uploader) {
        this.uploader = uploader;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
