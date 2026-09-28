package com.watcher.scheduler;

import com.watcher.config.AppConfig;
import com.watcher.security.SecurityAuditor;
import com.watcher.storage.LocalSpoolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class RetentionCleanerServiceTest {

    @Test
    public void testRunCleanupCycleLocalPurge(@TempDir Path tempDir) throws IOException {
        Path spoolDir = tempDir.resolve("spool");
        Path logDir = tempDir.resolve("logs");
        Files.createDirectories(spoolDir);
        Files.createDirectories(logDir);

        LocalSpoolManager spoolManager = new LocalSpoolManager(spoolDir, 10 * 1024 * 1024);
        Path oldFile = spoolManager.saveFrame("screenshot_old.jpg", "old data".getBytes());
        Path newFile = spoolManager.saveFrame("screenshot_new.jpg", "new data".getBytes());

        long thirtyFiveDaysAgo = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000);
        Files.setLastModifiedTime(oldFile, FileTime.fromMillis(thirtyFiveDaysAgo));

        AppConfig config = new AppConfig();
        config.setRetentionDays(30);

        SecurityAuditor auditor = new SecurityAuditor(logDir.toString());

        RetentionCleanerService service = new RetentionCleanerService(config, spoolManager, null, auditor);

        service.runCleanupCycle();

        assertFalse(Files.exists(oldFile), "Old file should be purged");
        assertTrue(Files.exists(newFile), "New file should be kept");

        Path auditLog = logDir.resolve("security_audit.log");
        assertTrue(Files.exists(auditLog));
        String logContent = Files.readString(auditLog);
        assertTrue(logContent.contains("LOCAL_RETENTION_PURGE"), "Audit log should record local retention purge");
    }

    @Test
    public void testServiceLifecycle(@TempDir Path tempDir) {
        Path spoolDir = tempDir.resolve("spool");
        Path logDir = tempDir.resolve("logs");

        LocalSpoolManager spoolManager = new LocalSpoolManager(spoolDir, 1024 * 1024);
        AppConfig config = new AppConfig();
        SecurityAuditor auditor = new SecurityAuditor(logDir.toString());

        RetentionCleanerService service = new RetentionCleanerService(config, spoolManager, null, auditor);

        assertFalse(service.isRunning());
        service.start(100, 100, TimeUnit.MILLISECONDS);
        assertTrue(service.isRunning());

        service.stop();
        assertFalse(service.isRunning());
    }
}
