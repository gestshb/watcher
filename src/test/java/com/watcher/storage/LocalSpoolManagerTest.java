package com.watcher.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LocalSpoolManagerTest {

    @Test
    public void testSaveAndGetPendingFiles(@TempDir Path tempDir) throws IOException {
        long maxSize = 10L * 1024 * 1024; // 10MB
        LocalSpoolManager manager = new LocalSpoolManager(tempDir, maxSize);

        byte[] sampleData1 = "Frame 1 data".getBytes();
        byte[] sampleData2 = "Frame 2 data".getBytes();

        manager.saveFrame("screenshot_1.jpg", sampleData1);
        manager.saveFrame("screenshot_2.jpg", sampleData2);

        List<Path> pending = manager.getPendingFiles();
        assertEquals(2, pending.size());

        // Test delete after upload
        boolean deleted = manager.deleteFile(pending.get(0));
        assertTrue(deleted);
        assertEquals(1, manager.getPendingFiles().size());
    }

    @Test
    public void testFifoDiskBoundsEnforcement(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Limit to 300 bytes
        long maxSize = 300L;
        LocalSpoolManager manager = new LocalSpoolManager(tempDir, maxSize);

        byte[] payload = new byte[100]; // 100 bytes each

        manager.saveFrame("screenshot_1.jpg", payload);
        Thread.sleep(10);
        manager.saveFrame("screenshot_2.jpg", payload);
        Thread.sleep(10);
        manager.saveFrame("screenshot_3.jpg", payload);
        Thread.sleep(10);

        assertEquals(3, manager.getPendingFiles().size());

        // Saving 4th frame exceeds 300 bytes (total would be 400), should evict oldest (screenshot_1.jpg)
        manager.saveFrame("screenshot_4.jpg", payload);

        List<Path> pendingAfterPurge = manager.getPendingFiles();
        assertTrue(pendingAfterPurge.size() <= 3);
        assertFalse(Files.exists(tempDir.resolve("screenshot_1.jpg")));
        assertTrue(Files.exists(tempDir.resolve("screenshot_4.jpg")));
    }

    @Test
    public void testPurgeOlderThan(@TempDir Path tempDir) throws IOException {
        LocalSpoolManager manager = new LocalSpoolManager(tempDir, 1024 * 1024);

        Path oldFile = manager.saveFrame("screenshot_old.jpg", "old content".getBytes());
        Path newFile = manager.saveFrame("screenshot_new.jpg", "new content".getBytes());

        // Set oldFile modified time to 35 days ago
        long thirtyFiveDaysAgo = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000);
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.fromMillis(thirtyFiveDaysAgo));

        // Purge older than 30 days
        int purged = manager.purgeOlderThan(30);

        assertEquals(1, purged);
        assertFalse(Files.exists(oldFile), "Old file should have been deleted");
        assertTrue(Files.exists(newFile), "New file should still exist");
    }
}
