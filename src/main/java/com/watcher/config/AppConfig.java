package com.watcher.config;

import java.io.Serializable;
import java.util.Objects;

/**
 * POJO containing all configurable parameters for Watcher.
 */
public class AppConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private int captureIntervalMinutes = 10;
    private float imageQuality = 0.6f;
    private String spoolDirectory = "./spool";
    private long maxSpoolSizeBytes = 500L * 1024L * 1024L; // 500 MB
    private boolean deleteAfterUpload = true;

    private String driveFolderName = "WatcherScreenshots";
    private int retentionDays = 30;
    private String credentialsPath = "./credentials.json";

    private String adminPasswordHash = "";
    private String adminPasswordSalt = "";

    public AppConfig() {
    }

    public int getCaptureIntervalMinutes() {
        return captureIntervalMinutes;
    }

    public void setCaptureIntervalMinutes(int captureIntervalMinutes) {
        if (captureIntervalMinutes < 1) {
            throw new IllegalArgumentException("Interval must be at least 1 minute");
        }
        this.captureIntervalMinutes = captureIntervalMinutes;
    }

    public float getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(float imageQuality) {
        if (imageQuality < 0.1f || imageQuality > 1.0f) {
            throw new IllegalArgumentException("Image quality must be between 0.1 and 1.0");
        }
        this.imageQuality = imageQuality;
    }

    public String getSpoolDirectory() {
        return spoolDirectory;
    }

    public void setSpoolDirectory(String spoolDirectory) {
        this.spoolDirectory = Objects.requireNonNull(spoolDirectory, "spoolDirectory cannot be null");
    }

    public long getMaxSpoolSizeBytes() {
        return maxSpoolSizeBytes;
    }

    public void setMaxSpoolSizeBytes(long maxSpoolSizeBytes) {
        this.maxSpoolSizeBytes = maxSpoolSizeBytes;
    }

    public boolean isDeleteAfterUpload() {
        return deleteAfterUpload;
    }

    public void setDeleteAfterUpload(boolean deleteAfterUpload) {
        this.deleteAfterUpload = deleteAfterUpload;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }

    public void setDriveFolderName(String driveFolderName) {
        this.driveFolderName = Objects.requireNonNull(driveFolderName, "driveFolderName cannot be null");
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Retention days must be at least 1");
        }
        this.retentionDays = retentionDays;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = Objects.requireNonNull(credentialsPath, "credentialsPath cannot be null");
    }

    public String getAdminPasswordHash() {
        return adminPasswordHash;
    }

    public void setAdminPasswordHash(String adminPasswordHash) {
        this.adminPasswordHash = adminPasswordHash;
    }

    public String getAdminPasswordSalt() {
        return adminPasswordSalt;
    }

    public void setAdminPasswordSalt(String adminPasswordSalt) {
        this.adminPasswordSalt = adminPasswordSalt;
    }
}
