package com.watcher.storage;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Periodically cleans up daily screenshot folders on Google Drive that exceed the retention policy.
 */
public class CloudRetentionCleaner {
    private static final Logger logger = LogManager.getLogger(CloudRetentionCleaner.class);
    private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Drive driveService;
    private final String rootFolderName;

    public CloudRetentionCleaner(Drive driveService, String rootFolderName) {
        this.driveService = driveService;
        this.rootFolderName = rootFolderName;
    }

    /**
     * Deletes daily folders under root folder older than retentionDays.
     */
    public int cleanOldFolders(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }

        try {
            // Find root folder ID
            FileList rootResult = driveService.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder' and trashed = false and name = '" + rootFolderName.replace("'", "\\'") + "'")
                    .setSpaces("drive")
                    .setFields("files(id)")
                    .execute();

            if (rootResult.getFiles() == null || rootResult.getFiles().isEmpty()) {
                logger.debug("Root folder '{}' not found in Drive. Nothing to clean.", rootFolderName);
                return 0;
            }

            String rootId = rootResult.getFiles().get(0).getId();

            // List all children folders of root
            FileList childrenResult = driveService.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder' and trashed = false and '" + rootId + "' in parents")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute();

            List<File> children = childrenResult.getFiles();
            if (children == null || children.isEmpty()) {
                return 0;
            }

            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            int deletedCount = 0;

            for (File child : children) {
                String folderName = child.getName();
                try {
                    LocalDate folderDate = LocalDate.parse(folderName, DATE_DIR_FORMAT);
                    if (folderDate.isBefore(cutoffDate)) {
                        driveService.files().delete(child.getId()).execute();
                        logger.info("Purged expired cloud folder '{}' (older than {} days)", folderName, retentionDays);
                        deletedCount++;
                    }
                } catch (DateTimeParseException ignored) {
                    // Not a date folder, skip
                }
            }

            return deletedCount;
        } catch (IOException e) {
            logger.error("Failed executing cloud retention cleaner", e);
            return 0;
        }
    }
}
