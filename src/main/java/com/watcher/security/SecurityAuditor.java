package com.watcher.security;

import com.watcher.config.PathResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tracks security and tampering events (unexpected restarts, password failures, offline buffer issues).
 */
public class SecurityAuditor {
    private static final Logger logger = LogManager.getLogger(SecurityAuditor.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path auditLogPath;

    public SecurityAuditor(String logDirPath) {
        this(PathResolver.resolve(logDirPath).resolve("security_audit.log"));
    }

    public SecurityAuditor(Path auditLogPath) {
        this.auditLogPath = PathResolver.resolve(auditLogPath);
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            Path parent = auditLogPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            logger.error("Could not create audit log directory", e);
        }
    }

    /**
     * Records a security event.
     */
    public synchronized void recordEvent(String eventType, String details) {
        String logLine = String.format("%s - [SECURITY_AUDIT] [%s] %s%n",
                LocalDateTime.now().format(TIME_FORMAT), eventType, details);

        logger.info("[AUDIT] [{}]: {}", eventType, details);

        try {
            ensureDirectoryExists();
            Files.writeString(auditLogPath, logLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("Failed to write to security audit file: {}", auditLogPath, e);
        }
    }

    public Path getAuditLogPath() {
        return auditLogPath;
    }
}
