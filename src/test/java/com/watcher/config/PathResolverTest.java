package com.watcher.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathResolverTest {

    @AfterEach
    public void cleanup() {
        System.clearProperty("watcher.base.dir");
        System.clearProperty("watcher.log.dir");
        PathResolver.resetCache();
    }

    @Test
    public void testBaseDirPropertyOverride(@TempDir Path tempDir) {
        System.setProperty("watcher.base.dir", tempDir.toAbsolutePath().toString());
        Path baseDir = PathResolver.getBaseDir();
        assertEquals(tempDir.toAbsolutePath().normalize(), baseDir);
    }

    @Test
    public void testResolveRelativePath(@TempDir Path tempDir) {
        System.setProperty("watcher.base.dir", tempDir.toAbsolutePath().toString());

        Path resolved = PathResolver.resolve("spool");
        assertEquals(tempDir.resolve("spool").toAbsolutePath().normalize(), resolved);

        Path resolvedConfig = PathResolver.getConfigPath();
        assertEquals(tempDir.resolve("config.dat").toAbsolutePath().normalize(), resolvedConfig);

        Path resolvedLogs = PathResolver.getLogsDir();
        assertEquals(tempDir.resolve("logs").toAbsolutePath().normalize(), resolvedLogs);
    }

    @Test
    public void testResolveAbsolutePath(@TempDir Path tempDir) {
        Path abs = tempDir.resolve("custom_folder").toAbsolutePath();
        Path resolved = PathResolver.resolve(abs);
        assertEquals(abs.normalize(), resolved);
    }

    @Test
    public void testResolveCredentialsFallback(@TempDir Path tempDir) throws IOException {
        System.setProperty("watcher.base.dir", tempDir.toAbsolutePath().toString());
        Path credFile = tempDir.resolve("credentials.json");
        Files.writeString(credFile, "{\"bucket\":\"test\"}");

        Path resolved = PathResolver.resolveCredentials("./credentials.json");
        assertEquals(credFile.toAbsolutePath().normalize(), resolved);
    }

    @Test
    public void testInitSystemProperties(@TempDir Path tempDir) {
        System.setProperty("watcher.base.dir", tempDir.toAbsolutePath().toString());
        PathResolver.initSystemProperties();

        assertNotNull(System.getProperty("watcher.log.dir"));
        assertTrue(System.getProperty("watcher.log.dir").endsWith("logs"));
    }
}
