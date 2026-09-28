package com.watcher;

import com.watcher.config.AppConfig;
import com.watcher.config.ConfigManager;
import com.watcher.storage.GoogleDriveUploader;
import com.watcher.storage.LocalSpoolManager;

import java.nio.file.Path;
import java.util.List;

public class TestUpload {
    public static void main(String[] args) {
        System.out.println("Starting Google Drive test upload...");
        try {
            ConfigManager configManager = new ConfigManager(Path.of("config.dat"));
            AppConfig config = configManager.load("SecretParent123".toCharArray());

            GoogleDriveUploader uploader = new GoogleDriveUploader(
                    config.getCredentialsPath(),
                    config.getDriveFolderName(),
                    "test-host"
            );

            LocalSpoolManager spool = new LocalSpoolManager(config.getSpoolDirectory(), config.getMaxSpoolSizeBytes());
            List<Path> files = spool.getPendingFiles();
            System.out.println("Pending files in spool: " + files.size());

            for (Path file : files) {
                System.out.println("Uploading: " + file.getFileName());
                String fileId = uploader.uploadFile(file, "image/jpeg");
                System.out.println("Uploaded successfully! Google Drive File ID: " + fileId);
                spool.deleteFile(file);
                System.out.println("Local file deleted after sync.");
            }

            System.out.println("Upload test completed successfully!");
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
