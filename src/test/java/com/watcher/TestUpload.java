package com.watcher;

import com.watcher.config.AppConfig;
import com.watcher.config.ConfigManager;
import com.watcher.storage.LocalSpoolManager;
import com.watcher.storage.S3Uploader;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class TestUpload {
    public static void main(String[] args) {
        System.out.println("Starting S3 test upload...");
        try {
            ConfigManager configManager = new ConfigManager(Path.of("config.dat"));
            AppConfig config = configManager.load("SecretParent123".toCharArray());

            S3Uploader uploader = new S3Uploader(
                    config.getCredentialsPath(),
                    config.getS3BucketName(),
                    config.getS3Region(),
                    config.getS3AccessKey(),
                    config.getS3SecretKey(),
                    config.getS3Endpoint(),
                    "test-host"
            );

            LocalSpoolManager spool = new LocalSpoolManager(config.getSpoolDirectory(), config.getMaxSpoolSizeBytes());
            List<Path> files = spool.getPendingFiles();
            System.out.println("Pending files in spool: " + files.size());

            String dateStr = LocalDate.now().toString();
            for (Path file : files) {
                System.out.println("Uploading: " + file.getFileName());
                boolean success = uploader.uploadScreenshot(file, dateStr);
                if (success) {
                    System.out.println("Uploaded successfully to S3!");
                    spool.deleteFile(file);
                    System.out.println("Local file deleted after sync.");
                } else {
                    System.err.println("Failed to upload: " + file.getFileName());
                }
            }

            System.out.println("Upload test completed!");
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
