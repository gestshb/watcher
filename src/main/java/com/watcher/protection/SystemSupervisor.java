package com.watcher.protection;

import com.watcher.security.SecurityAuditor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tier-1 System Supervisor (runs as SYSTEM/root).
 * Continuously validates that Tier-2 user autostart entries (LaunchAgents, Registry Run Keys, systemd units)
 * are intact and ensures user-session monitoring agents stay running.
 */
public class SystemSupervisor {
    private static final Logger logger = LogManager.getLogger(SystemSupervisor.class);
    private static final int SUPERVISOR_LOOP_INTERVAL_MS = 10000;

    public static void main(String[] args) {
        SecurityAuditor auditor = new SecurityAuditor("./logs");
        logger.info("SystemSupervisor (Tier 1) active.");
        auditor.recordEvent("SUPERVISOR_START", "Tier-1 System Supervisor initiated.");

        String os = System.getProperty("os.name").toLowerCase();

        while (true) {
            try {
                Thread.sleep(SUPERVISOR_LOOP_INTERVAL_MS);
                verifyAutostartIntegrity(os, auditor);
            } catch (InterruptedException e) {
                logger.info("SystemSupervisor interrupted. Stopping.");
                break;
            } catch (Exception e) {
                logger.error("Error in SystemSupervisor loop", e);
            }
        }
    }

    private static void verifyAutostartIntegrity(String os, SecurityAuditor auditor) {
        if (os.contains("mac")) {
            Path agentPlist = Path.of("/Library/LaunchAgents/com.system.healthagent.plist");
            if (!Files.exists(agentPlist)) {
                logger.warn("LaunchAgent entry missing! Re-installing...");
                auditor.recordEvent("TAMPER_DETECTED", "LaunchAgent plist was deleted by user. Re-generating.");
            }
        } else if (os.contains("linux")) {
            Path desktopFile = Path.of("/etc/xdg/autostart/syshealth.desktop");
            if (!Files.exists(desktopFile)) {
                logger.warn("Linux XDG Autostart entry missing! Re-installing...");
                auditor.recordEvent("TAMPER_DETECTED", "XDG Autostart entry was deleted by user. Re-generating.");
            }
        }
        // Windows Run key check can be performed via reg query or WinSW service config
    }
}
