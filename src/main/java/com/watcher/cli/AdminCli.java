package com.watcher.cli;

import com.watcher.config.AppConfig;
import com.watcher.config.ConfigEncryptor;
import com.watcher.config.ConfigManager;
import com.watcher.storage.LocalSpoolManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Password-protected Administration CLI for configuring, inspecting, and managing Watcher.
 */
@Command(name = "watcher-admin",
        mixinStandardHelpOptions = true,
        version = "Watcher 1.0.0",
        description = "Administrative management interface for Watcher monitoring daemon.")
public class AdminCli implements Callable<Integer> {

    @Option(names = {"-p", "--password"}, required = true, description = "Admin password for decrypting settings")
    private String password;

    @Option(names = {"-s", "--status"}, description = "Display current status, queue stats, and active config")
    private boolean status;

    @Option(names = {"-i", "--set-interval"}, description = "Set screenshot capture interval in minutes")
    private Integer interval;

    @Option(names = {"-r", "--set-retention"}, description = "Set cloud retention period in days")
    private Integer retentionDays;

    @Option(names = {"-q", "--set-quality"}, description = "Set image compression quality (0.1 to 1.0)")
    private Float quality;

    @Option(names = {"-c", "--change-password"}, description = "Change the admin password to a new value")
    private String newPassword;

    @Option(names = {"--uninstall"}, description = "Completely uninstall and remove local files")
    private boolean uninstall;

    @Override
    public Integer call() {
        try {
            Path configPath = Path.of("config.dat");
            ConfigManager configManager = new ConfigManager(configPath);

            AppConfig config;
            try {
                config = configManager.load(password.toCharArray());
            } catch (Exception e) {
                System.err.println("Authentication failed: Invalid password or corrupted configuration.");
                return 1;
            }

            boolean configModified = false;

            if (interval != null) {
                if (interval < 1) {
                    System.err.println("Interval must be at least 1 minute.");
                    return 1;
                }
                config.setCaptureIntervalMinutes(interval);
                configModified = true;
                System.out.printf("Capture interval set to %d minutes.%n", interval);
            }

            if (retentionDays != null) {
                if (retentionDays < 1) {
                    System.err.println("Retention must be at least 1 day.");
                    return 1;
                }
                config.setRetentionDays(retentionDays);
                configModified = true;
                System.out.printf("Retention period set to %d days.%n", retentionDays);
            }

            if (quality != null) {
                if (quality < 0.1f || quality > 1.0f) {
                    System.err.println("Quality must be between 0.1 and 1.0.");
                    return 1;
                }
                config.setImageQuality(quality);
                configModified = true;
                System.out.printf("Compression quality set to %.2f.%n", quality);
            }

            char[] savePassword = password.toCharArray();
            if (newPassword != null && !newPassword.isEmpty()) {
                savePassword = newPassword.toCharArray();
                configModified = true;
                System.out.println("Admin password changed successfully.");
            }

            if (configModified) {
                configManager.save(config, savePassword);
                System.out.println("Configuration updated and securely saved.");
            }

            if (status) {
                printStatus(config);
            }

            if (uninstall) {
                System.out.println("Initiating complete uninstall...");
                // Remove spool files and config
                LocalSpoolManager spool = new LocalSpoolManager(config.getSpoolDirectory(), config.getMaxSpoolSizeBytes());
                for (Path p : spool.getPendingFiles()) {
                    Files.deleteIfExists(p);
                }
                Files.deleteIfExists(configPath);
                System.out.println("Local data removed successfully. Please stop the service and remove the binary.");
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Admin CLI operation failed: " + e.getMessage());
            return 1;
        }
    }

    private void printStatus(AppConfig config) {
        System.out.println("=========================================");
        System.out.println("        WATCHER MONITORING STATUS        ");
        System.out.println("=========================================");
        System.out.printf("Capture Interval  : %d minutes%n", config.getCaptureIntervalMinutes());
        System.out.printf("Image Quality     : %.2f%n", config.getImageQuality());
        System.out.printf("Spool Directory   : %s%n", config.getSpoolDirectory());
        System.out.printf("Drive Folder Name : %s%n", config.getDriveFolderName());
        System.out.printf("Cloud Retention   : %d days%n", config.getRetentionDays());
        System.out.printf("Delete After Sync : %b%n", config.isDeleteAfterUpload());

        try {
            LocalSpoolManager spool = new LocalSpoolManager(config.getSpoolDirectory(), config.getMaxSpoolSizeBytes());
            int pendingCount = spool.getPendingFiles().size();
            System.out.printf("Pending Files     : %d%n", pendingCount);
        } catch (Exception ignored) {
        }
        System.out.println("=========================================");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AdminCli()).execute(args);
        System.exit(exitCode);
    }
}
