package com.watcher;

import com.watcher.capture.ScreenCapture;
import com.watcher.cli.AdminCli;
import com.watcher.config.AppConfig;
import com.watcher.config.ConfigManager;
import com.watcher.config.PathResolver;
import com.watcher.protection.ProcessGuard;
import com.watcher.scheduler.CaptureScheduler;
import com.watcher.scheduler.RetentionCleanerService;
import com.watcher.security.SecurityAuditor;
import com.watcher.storage.LocalSpoolManager;
import com.watcher.storage.S3Uploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main application bootstrap for Watcher.
 * Handles both the background worker execution and administrative CLI invocations.
 */
public class App {
    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        PathResolver.initSystemProperties();

        // If CLI flags are passed, delegate to Admin CLI
        if (args.length > 0 && (args[0].startsWith("-") || args[0].equals("admin"))) {
            java.util.List<String> cliArgs = new java.util.ArrayList<>();
            for (String arg : args) {
                if (!arg.equals("admin") && !arg.equals("--admin")) {
                    cliArgs.add(arg);
                }
            }
            int exitCode = new CommandLine(new AdminCli()).execute(cliArgs.toArray(new String[0]));
            System.exit(exitCode);
            return;
        }

        logger.info("Initializing Watcher Monitoring Daemon...");
        SecurityAuditor auditor = new SecurityAuditor(PathResolver.getLogsDir().toString());

        // Single instance guard per OS user session to avoid duplicate daemons capturing concurrently
        String currentUser = System.getProperty("user.name", "default").replaceAll("[^a-zA-Z0-9._-]", "_");
        Path lockPath = PathResolver.getLogsDir().resolve(".daemon_" + currentUser + ".lock");
        java.nio.channels.FileChannel lockChannel = null;
        java.nio.channels.FileLock lock = null;
        try {
            if (lockPath.getParent() != null && !Files.exists(lockPath.getParent())) {
                Files.createDirectories(lockPath.getParent());
            }
            lockChannel = java.nio.channels.FileChannel.open(
                    lockPath,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE
            );
            lock = lockChannel.tryLock();
            if (lock == null) {
                logger.info("Another instance of Watcher is already running for user [{}]. Exiting gracefully.", currentUser);
                System.exit(0);
                return;
            }
        } catch (Exception e) {
            logger.debug("Single-instance lock check note for user [{}]: {}", currentUser, e.getMessage());
        }

        try {
            Path configPath = PathResolver.getConfigPath();
            ConfigManager configManager = new ConfigManager(configPath);

            AppConfig config;
            // In headless auto-daemon mode, look for environment or local fallback
            String adminSecret = System.getenv("WATCHER_SECRET");
            if (adminSecret != null && !adminSecret.isEmpty()) {
                config = configManager.load(adminSecret.toCharArray());
            } else if (Files.exists(configPath)) {
                String propSecret = System.getProperty("watcher.secret");
                if (propSecret != null && !propSecret.isEmpty()) {
                    config = configManager.load(propSecret.toCharArray());
                } else {
                    try {
                        config = configManager.load("SecretParent123".toCharArray());
                    } catch (Exception ignored) {
                        config = configManager.load("default_watcher_key".toCharArray());
                    }
                }
            } else {
                config = configManager.load(null);
            }

            ScreenCapture screenCapture = new ScreenCapture();
            LocalSpoolManager spoolManager = new LocalSpoolManager(config.getSpoolDirectory(), config.getMaxSpoolSizeBytes());

            S3Uploader uploader = null;
            try {
                uploader = new S3Uploader(
                        config.getCredentialsPath(),
                        config.getS3BucketName(),
                        config.getS3Region(),
                        config.getS3AccessKey(),
                        config.getS3SecretKey(),
                        config.getS3Endpoint(),
                        screenCapture.getHostname()
                );
                logger.info("S3 Uploader initialized successfully for bucket [{}].", config.getS3BucketName());
            } catch (Exception e) {
                logger.warn("Could not initialize S3 Uploader: {}. Captures will accumulate in spool queue.", e.getMessage());
            }

            CaptureScheduler scheduler = new CaptureScheduler(config, screenCapture, spoolManager, uploader, auditor);
            scheduler.start();

            RetentionCleanerService retentionService = new RetentionCleanerService(config, spoolManager, uploader, auditor);
            retentionService.start();

            // Spawn Watchdog supervisor process or adopt existing watchdog
            long watchdogPid = -1;
            ProcessGuard guard = null;
            try {
                String jarPath = ProcessGuard.getRunningJarPath();
                guard = new ProcessGuard(auditor, jarPath);
                String existingWatchdogProp = System.getProperty("watcher.watchdog.pid");
                if (existingWatchdogProp != null && !existingWatchdogProp.isBlank()) {
                    try {
                        long candidatePid = Long.parseLong(existingWatchdogProp);
                        if (guard.isProcessAlive(candidatePid)) {
                            watchdogPid = candidatePid;
                            logger.info("Adopted existing Watchdog supervisor PID: {}", watchdogPid);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (watchdogPid <= 0) {
                    Process watchdogProcess = guard.launchWatchdog(ProcessHandle.current().pid());
                    watchdogPid = watchdogProcess.pid();
                }
            } catch (Exception e) {
                logger.warn("Could not spawn secondary watchdog process: {}", e.getMessage());
            }

            // Register shutdown hook for graceful exit if requested
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received. Shutting down cleanly...");
                scheduler.stop();
                retentionService.stop();
            }));

            // Keep daemon main thread alive and mutually supervise watchdog
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(3000);
                if (guard != null && watchdogPid > 0 && !guard.isProcessAlive(watchdogPid)) {
                    logger.warn("Watchdog PID {} died! Resurrecting watchdog supervisor...", watchdogPid);
                    auditor.recordEvent("TAMPER_DETECTED", "Watchdog PID " + watchdogPid + " died! Resurrecting.");
                    try {
                        Process newWatchdog = guard.launchWatchdog(ProcessHandle.current().pid());
                        watchdogPid = newWatchdog.pid();
                        logger.info("New watchdog supervisor running under PID: {}", watchdogPid);
                    } catch (Exception e) {
                        logger.error("Failed to resurrect watchdog supervisor", e);
                    }
                }
            }

        } catch (Exception e) {
            logger.fatal("Fatal error in Watcher daemon", e);
            auditor.recordEvent("FATAL_ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
