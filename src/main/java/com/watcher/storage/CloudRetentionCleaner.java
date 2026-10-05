package com.watcher.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Periodically cleans up daily screenshot folders on S3 that exceed the retention policy.
 * Objects are structured under date prefixes: YYYY-MM-DD/
 */
public class CloudRetentionCleaner {
    private static final Logger logger = LogManager.getLogger(CloudRetentionCleaner.class);
    private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S3Client s3Client;
    private final String bucketName;

    public CloudRetentionCleaner(S3Client s3Client, String bucketName) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
    }

    /**
     * Finds and deletes all objects with date prefixes older than retentionDays.
     * @param retentionDays maximum age in days
     * @return number of date prefix folders deleted
     */
    public int cleanOldFolders(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }

        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
            int deletedFolders = 0;
            String continuationToken = null;

            do {
                ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .delimiter("/");
                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response listResponse = s3Client.listObjectsV2(reqBuilder.build());
                List<CommonPrefix> prefixes = listResponse.commonPrefixes();

                if (prefixes != null) {
                    for (CommonPrefix cp : prefixes) {
                        String prefixStr = cp.prefix(); // e.g. "2026-08-01/"
                        String dateCandidate = prefixStr.endsWith("/")
                                ? prefixStr.substring(0, prefixStr.length() - 1)
                                : prefixStr;

                        try {
                            LocalDate folderDate = LocalDate.parse(dateCandidate, DATE_DIR_FORMAT);
                            if (folderDate.isBefore(cutoffDate)) {
                                logger.info("Found expired date prefix [{}] in S3 bucket [{}] (older than {} days). Deleting contents...",
                                        prefixStr, bucketName, retentionDays);
                                deleteObjectsWithPrefix(prefixStr);
                                deletedFolders++;
                            }
                        } catch (DateTimeParseException ignored) {
                            // Skip prefixes that do not match date format
                        }
                    }
                }
                continuationToken = listResponse.nextContinuationToken();
            } while (continuationToken != null);

            return deletedFolders;
        } catch (Exception e) {
            logger.error("Error cleaning expired folders in S3 bucket [{}]: {}", bucketName, e.getMessage());
            return 0;
        }
    }

    private void deleteObjectsWithPrefix(String prefix) {
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix);
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());
            List<S3Object> contents = response.contents();

            if (contents != null && !contents.isEmpty()) {
                List<ObjectIdentifier> toDelete = new ArrayList<>();
                for (S3Object obj : contents) {
                    toDelete.add(ObjectIdentifier.builder().key(obj.key()).build());
                }

                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(toDelete).build())
                        .build();

                s3Client.deleteObjects(deleteRequest);
                logger.info("Deleted {} expired objects under prefix [{}] from S3", toDelete.size(), prefix);
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
    }
}
