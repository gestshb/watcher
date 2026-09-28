package com.watcher.protection;

import com.watcher.security.SecurityAuditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessGuardTest {

    @Test
    public void testIsCurrentProcessAlive(@TempDir Path tempDir) {
        SecurityAuditor auditor = new SecurityAuditor(tempDir.resolve("audit.log"));
        ProcessGuard guard = new ProcessGuard(auditor, "dummy.jar");

        long currentPid = ProcessHandle.current().pid();
        assertTrue(guard.isProcessAlive(currentPid));

        // Invalid or extreme PID should return false
        assertFalse(guard.isProcessAlive(-999));
        assertFalse(guard.isProcessAlive(99999999));
    }
}
