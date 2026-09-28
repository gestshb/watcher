package com.watcher.protection;

import com.watcher.security.SecurityAuditor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Watchdog daemon process that monitors the main worker process and resurrects it if killed.
 */
public class Watchdog {
    private static final Logger logger = LogManager.getLogger(Watchdog.class);
    private static final int CHECK_INTERVAL_MS = 3000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Watchdog <main-process-pid>");
            System.exit(1);
        }

        long targetPid = Long.parseLong(args[0]);
        String jarPath = ProcessGuard.getRunningJarPath();
        SecurityAuditor auditor = new SecurityAuditor("./logs");
        ProcessGuard guard = new ProcessGuard(auditor, jarPath);

        logger.info("Watchdog started. Monitoring PID: {}", targetPid);
        auditor.recordEvent("WATCHDOG_START", "Watchdog active, monitoring PID " + targetPid);

        while (true) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
                if (!guard.isProcessAlive(targetPid)) {
                    logger.warn("Target worker PID {} died! Resurrecting worker immediately...", targetPid);
                    auditor.recordEvent("TAMPER_DETECTED", "Worker PID " + targetPid + " was terminated unexpectedly!");

                    Process newWorker = guard.launchMainWorker();
                    targetPid = newWorker.pid();
                    logger.info("New worker running under PID: {}", targetPid);
                }
            } catch (InterruptedException e) {
                logger.info("Watchdog interrupted. Exiting.");
                break;
            } catch (Exception e) {
                logger.error("Error in watchdog supervision loop", e);
            }
        }
    }
}
