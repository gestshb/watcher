package com.watcher.protection;

import com.watcher.config.PathResolver;
import com.watcher.security.SecurityAuditor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessHandle;
import java.util.Optional;

/**
 * Supervised process monitoring. Checks whether the target process is alive,
 * and relaunches it upon termination.
 */
public class ProcessGuard {
    private static final Logger logger = LogManager.getLogger(ProcessGuard.class);

    private final SecurityAuditor auditor;
    private final String jarPath;

    public ProcessGuard(SecurityAuditor auditor, String jarPath) {
        this.auditor = auditor;
        this.jarPath = jarPath;
    }

    /**
     * Checks if a process with the given PID is currently active.
     */
    public boolean isProcessAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        return handle.isPresent() && handle.get().isAlive();
    }

    /**
     * Launches the Watchdog companion process.
     */
    public Process launchWatchdog(long parentPid) throws IOException {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-Dsun.java2d.d3d=false",
                "-Dsun.java2d.noddraw=true",
                "-Dwatcher.base.dir=" + PathResolver.getBaseDir().toAbsolutePath(),
                "-Dwatcher.log.dir=" + PathResolver.getLogsDir().toAbsolutePath(),
                "-cp", jarPath,
                "com.watcher.protection.Watchdog",
                String.valueOf(parentPid)
        );
        pb.directory(PathResolver.getBaseDir().toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        logger.info("Spawned Watchdog process with PID {}", p.pid());
        if (auditor != null) {
            auditor.recordEvent("WATCHDOG_SPAWN", "Launched Watchdog process PID " + p.pid());
        }
        return p;
    }

    /**
     * Launches the Main Worker process.
     */
    public Process launchMainWorker() throws IOException {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-Dsun.java2d.d3d=false",
                "-Dsun.java2d.noddraw=true",
                "-Dwatcher.watchdog.pid=" + ProcessHandle.current().pid(),
                "-Dwatcher.base.dir=" + PathResolver.getBaseDir().toAbsolutePath(),
                "-Dwatcher.log.dir=" + PathResolver.getLogsDir().toAbsolutePath(),
                "-jar", jarPath
        );
        pb.directory(PathResolver.getBaseDir().toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        logger.info("Spawned Main Worker process with PID {}", p.pid());
        if (auditor != null) {
            auditor.recordEvent("WORKER_RESURRECT", "Main worker process resurrected PID " + p.pid());
        }
        return p;
    }

    /**
     * Finds the running JAR path of the current execution.
     */
    public static String getRunningJarPath() {
        try {
            return new File(ProcessGuard.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getAbsolutePath();
        } catch (Exception e) {
            return "syshealthd.jar";
        }
    }
}
