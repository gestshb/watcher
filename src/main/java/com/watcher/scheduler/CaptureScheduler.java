package com.watcher.scheduler;

import com.watcher.capture.ScreenCapture;
import com.watcher.config.AppConfig;
import com.watcher.security.SecurityAuditor;
import com.watcher.storage.LocalSpoolManager;
import com.watcher.storage.S3Uploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

/**
 * Periodically captures screens, spools locally, and syncs to S3 cloud storage.
 */
public class CaptureScheduler {
    private static final Logger logger = LogManager.getLogger(CaptureScheduler.class);

    private final AppConfig config;
    private final ScreenCapture screenCapture;
    private final LocalSpoolManager spoolManager;
    private final S3Uploader uploader;
    private final SecurityAuditor auditor;
    private final ScheduledExecutorService scheduler;

    private volatile boolean isPaused = false;
    private volatile long pauseUntilEpochMs = 0;

    public CaptureScheduler(AppConfig config,
                            ScreenCapture screenCapture,
                            LocalSpoolManager spoolManager,
                            S3Uploader uploader,
                            SecurityAuditor auditor) {
        this.config = config;
        this.screenCapture = screenCapture;
        this.spoolManager = spoolManager;
        this.uploader = uploader;
        this.auditor = auditor;

        ThreadFactory threadFactory = runnable -> {
            Thread t = new Thread(runnable, "capture-scheduler");
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    /**
     * Starts the periodic scheduling loop.
     */
    public void start() {
        int intervalMinutes = config.getCaptureIntervalMinutes();
        logger.info("Starting CaptureScheduler with an interval of {} minutes.", intervalMinutes);
        auditor.recordEvent("SERVICE_START", "Monitoring daemon started with interval " + intervalMinutes + "m");

        // Initial delay 10 seconds, then periodic
        scheduler.scheduleWithFixedDelay(this::runCaptureCycle, 10, intervalMinutes * 60L, TimeUnit.SECONDS);
    }

    /**
     * Executes one complete capture and upload iteration.
     */
    public synchronized void runCaptureCycle() {
        if (isPaused) {
            if (System.currentTimeMillis() < pauseUntilEpochMs) {
                logger.debug("Capture cycle skipped (monitoring currently paused).");
                return;
            } else {
                isPaused = false;
                auditor.recordEvent("RESUMED", "Monitoring resumed automatically after pause interval expired.");
            }
        }

        try {
            // 1. Capture screen(s)
            List<ScreenCapture.CapturedFrame> frames = screenCapture.captureAllDisplays(config.getImageQuality());
            if (frames.isEmpty()) {
                logger.debug("No active displays captured (display may be locked, asleep, or headless).");
            }
            for (ScreenCapture.CapturedFrame frame : frames) {
                spoolManager.saveFrame(frame.getFilename(), frame.getJpegData());
            }

            // 2. Upload any pending spooled files
            syncPendingSpool();
        } catch (Exception e) {
            logger.error("Error encountered during capture cycle", e);
            auditor.recordEvent("CAPTURE_ERROR", "Error during capture cycle: " + e.getMessage());
        }
    }

    /**
     * Syncs all queued local files to S3.
     */
    public void syncPendingSpool() {
        if (uploader == null) {
            logger.debug("S3 uploader is not configured. Retaining files locally in spool queue.");
            return;
        }

        try {
            List<Path> pending = spoolManager.getPendingFiles();
            String todayStr = LocalDate.now().toString();

            for (Path file : pending) {
                String filename = file.getFileName().toString();
                boolean success = uploader.uploadScreenshot(file, todayStr);
                if (success) {
                    if (config.isDeleteAfterUpload()) {
                        spoolManager.deleteFile(file);
                    }
                } else {
                    logger.warn("Sync failed for {}. Will retry next cycle.", filename);
                    break; // stop trying subsequent files if network is failing
                }
            }
        } catch (Exception e) {
            logger.error("Failed synchronizing local spool queue", e);
        }
    }

    /**
     * Pauses captures for the specified duration (minutes).
     */
    public void pause(int minutes) {
        this.isPaused = true;
        this.pauseUntilEpochMs = System.currentTimeMillis() + (minutes * 60L * 1000L);
        auditor.recordEvent("PAUSED", "Monitoring paused by admin for " + minutes + " minutes.");
    }

    public boolean isPaused() {
        return isPaused && System.currentTimeMillis() < pauseUntilEpochMs;
    }

    /**
     * Gracefully terminates the scheduler.
     */
    public void stop() {
        logger.info("Stopping CaptureScheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        auditor.recordEvent("SERVICE_STOP", "Monitoring daemon stopped.");
    }
}
