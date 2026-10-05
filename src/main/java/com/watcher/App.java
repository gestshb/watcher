package com.watcher;

import com.watcher.capture.ScreenCapture;
import com.watcher.cli.AdminCli;
import com.watcher.config.AppConfig;
import com.watcher.config.ConfigManager;
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
        SecurityAuditor auditor = new SecurityAuditor("./logs");

        try {
            Path configPath = Path.of("config.dat");
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

            // Spawn Watchdog supervisor process
            try {
                String jarPath = ProcessGuard.getRunningJarPath();
                ProcessGuard guard = new ProcessGuard(auditor, jarPath);
                guard.launchWatchdog(ProcessHandle.current().pid());
            } catch (Exception e) {
                logger.warn("Could not spawn secondary watchdog process: {}", e.getMessage());
            }

            // Register shutdown hook for graceful exit if requested
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received. Shutting down cleanly...");
                scheduler.stop();
                retentionService.stop();
            }));

            // Keep daemon main thread alive
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(60000);
            }

        } catch (Exception e) {
            logger.fatal("Fatal error in Watcher daemon", e);
            auditor.recordEvent("FATAL_ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
