package com.watcher.storage;

import com.watcher.config.PathResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the offline screenshot buffer queue with strict FIFO disk bounds.
 */
public class LocalSpoolManager {
    private static final Logger logger = LogManager.getLogger(LocalSpoolManager.class);

    private final Path spoolDir;
    private final long maxSizeBytes;

    public LocalSpoolManager(String spoolDirPath, long maxSizeBytes) {
        this(PathResolver.resolve(spoolDirPath), maxSizeBytes);
    }

    public LocalSpoolManager(Path spoolDir, long maxSizeBytes) {
        this.spoolDir = PathResolver.resolve(spoolDir);
        this.maxSizeBytes = maxSizeBytes;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(spoolDir)) {
                Files.createDirectories(spoolDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create spool directory: {}", spoolDir, e);
        }
    }

    /**
     * Saves a captured frame payload to the local spool queue and enforces FIFO disk limits.
     */
    public Path saveFrame(String filename, byte[] data) throws IOException {
        ensureDirectoryExists();
        Path filePath = spoolDir.resolve(filename);
        Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.debug("Saved frame to spool: {} ({} bytes)", filename, data.length);

        enforceDiskBounds();
        return filePath;
    }

    /**
     * Returns all pending spooled image files ordered from oldest to newest.
     */
    public List<Path> getPendingFiles() throws IOException {
        if (!Files.exists(spoolDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(spoolDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jpg") || p.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Deletes a spooled file after successful upload.
     */
    public boolean deleteFile(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Could not delete uploaded spool file: {}", file, e);
            return false;
        }
    }

    /**
     * Enforces the maximum size constraint by deleting oldest files first (FIFO).
     */
    public synchronized void enforceDiskBounds() {
        try {
            List<Path> files = getPendingFiles();
            long totalSize = 0;
            for (Path p : files) {
                totalSize += Files.size(p);
            }

            if (totalSize <= maxSizeBytes) {
                return;
            }

            logger.warn("Spool directory exceeded max size ({} > {} bytes). Purging oldest files...",
                    totalSize, maxSizeBytes);

            for (Path oldest : files) {
                if (totalSize <= maxSizeBytes) {
                    break;
                }
                long size = Files.size(oldest);
                if (Files.deleteIfExists(oldest)) {
                    totalSize -= size;
                    logger.info("Purged oldest file to respect disk quota: {}", oldest.getFileName());
                }
            }
        } catch (IOException e) {
            logger.error("Error while enforcing spool disk bounds", e);
        }
    }

    /**
     * Purges spooled files older than the specified retention days.
     *
     * @param retentionDays number of days after which a file is considered expired
     * @return number of deleted files
     */
    public synchronized int purgeOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }

        int deletedCount = 0;
        try {
            List<Path> files = getPendingFiles();
            long cutoffMillis = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);

            for (Path file : files) {
                try {
                    long lastModified = Files.getLastModifiedTime(file).toMillis();
                    if (lastModified < cutoffMillis) {
                        if (Files.deleteIfExists(file)) {
                            deletedCount++;
                            logger.info("Purged expired local spool file: {}", file.getFileName());
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Could not inspect or delete spool file during retention cleanup: {}", file, e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed executing local spool retention purge", e);
        }

        return deletedCount;
    }

    public Path getSpoolDir() {
        return spoolDir;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }
}
