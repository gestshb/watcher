package com.watcher.config;

import com.watcher.protection.ProcessGuard;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralized path resolution helper for Watcher.
 * Guarantees that configuration, logs, spooled captures, and credentials
 * resolve to the correct application base directory regardless of whether
 * the process was started from the desktop, CLI, or Windows autostart (where CWD is C:\Windows\System32).
 */
public final class PathResolver {

    private static volatile Path cachedBaseDir = null;

    private PathResolver() {
    }

    public static void resetCache() {
        cachedBaseDir = null;
    }

    /**
     * Resolves the primary base directory of the Watcher installation.
     */
    public static Path getBaseDir() {
        // 1. Explicit system property override (always evaluated dynamically)
        String propBase = System.getProperty("watcher.base.dir");
        if (propBase != null && !propBase.isBlank()) {
            return Path.of(propBase).toAbsolutePath().normalize();
        }

        if (cachedBaseDir != null) {
            return cachedBaseDir;
        }

        // 2. Directory containing the running JAR
        try {
            String runningJar = ProcessGuard.getRunningJarPath();
            if (runningJar != null && !runningJar.isBlank()) {
                File jarFile = new File(runningJar);
                File parent = jarFile.getParentFile();
                if (parent != null && parent.exists() && parent.isDirectory()) {
                    Path parentPath = parent.toPath().toAbsolutePath().normalize();
                    // If running inside standard install dir, use it directly
                    if (isStandardInstallDir(parentPath) || Files.exists(parentPath.resolve("credentials.json")) || Files.exists(parentPath.resolve("config.dat"))) {
                        cachedBaseDir = parentPath;
                        return parentPath;
                    }
                    // If running from target/ directory during development, check project root
                    if (parentPath.getFileName() != null && parentPath.getFileName().toString().equalsIgnoreCase("target")) {
                        Path projectRoot = parentPath.getParent();
                        if (projectRoot != null && Files.exists(projectRoot.resolve("pom.xml"))) {
                            cachedBaseDir = projectRoot;
                            return projectRoot;
                        }
                    }
                    cachedBaseDir = parentPath;
                    return parentPath;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Known platform-specific system directories if they exist
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Path winPath = Path.of("C:\\ProgramData\\SystemHealth");
            if (Files.exists(winPath)) {
                cachedBaseDir = winPath;
                return winPath;
            }
        } else if (os.contains("mac")) {
            Path macPath = Path.of("/Library/Application Support/SystemHealth");
            if (Files.exists(macPath)) {
                cachedBaseDir = macPath;
                return macPath;
            }
        } else if (os.contains("linux")) {
            Path linuxPath = Path.of("/var/lib/systemhealth");
            if (Files.exists(linuxPath)) {
                cachedBaseDir = linuxPath;
                return linuxPath;
            }
        }

        // 4. Default fallback to current working directory
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        cachedBaseDir = cwd;
        return cwd;
    }

    private static boolean isStandardInstallDir(Path path) {
        String str = path.toString().toLowerCase();
        return str.contains("systemhealth");
    }

    /**
     * Resolves a relative or absolute path against the application base directory.
     */
    public static Path resolve(String relativeOrAbsolutePath) {
        if (relativeOrAbsolutePath == null || relativeOrAbsolutePath.isBlank()) {
            return getBaseDir();
        }
        Path path = Path.of(relativeOrAbsolutePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return getBaseDir().resolve(path).normalize();
    }

    public static Path resolve(Path path) {
        if (path == null) {
            return getBaseDir();
        }
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return getBaseDir().resolve(path).normalize();
    }

    /**
     * Returns the resolved path to config.dat.
     */
    public static Path getConfigPath() {
        return resolve("config.dat");
    }

    /**
     * Returns the resolved path to the logs directory.
     */
    public static Path getLogsDir() {
        return resolve("logs");
    }

    /**
     * Returns the resolved path to the spool directory.
     */
    public static Path getSpoolDir() {
        return resolve("spool");
    }

    /**
     * Resolves credentials.json with multi-location fallback.
     */
    public static Path resolveCredentials(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path resolved = resolve(configuredPath);
            if (Files.exists(resolved)) {
                return resolved;
            }
            Path raw = Path.of(configuredPath);
            if (Files.exists(raw)) {
                return raw.toAbsolutePath().normalize();
            }
        }

        // Check base dir credentials.json
        Path baseCred = getBaseDir().resolve("credentials.json");
        if (Files.exists(baseCred)) {
            return baseCred;
        }

        // Check CWD credentials.json
        Path cwdCred = Path.of("credentials.json").toAbsolutePath().normalize();
        if (Files.exists(cwdCred)) {
            return cwdCred;
        }

        // Check parent of CWD
        Path parentCred = Path.of("..", "credentials.json").toAbsolutePath().normalize();
        if (Files.exists(parentCred)) {
            return parentCred;
        }

        // Check platform default install dir
        Path winPath = Path.of("C:\\ProgramData\\SystemHealth\\credentials.json");
        if (Files.exists(winPath)) {
            return winPath;
        }

        return baseCred;
    }

    /**
     * Initializes critical system properties (e.g. for Log4j2 log directory).
     * Must be called as early as possible before LogManager initializes.
     */
    public static void initSystemProperties() {
        if (System.getProperty("sun.java2d.d3d") == null) {
            System.setProperty("sun.java2d.d3d", "false");
        }
        if (System.getProperty("sun.java2d.noddraw") == null) {
            System.setProperty("sun.java2d.noddraw", "true");
        }
        Path base = getBaseDir();
        if (System.getProperty("watcher.base.dir") == null) {
            System.setProperty("watcher.base.dir", base.toAbsolutePath().toString());
        }
        if (System.getProperty("watcher.log.dir") == null) {
            System.setProperty("watcher.log.dir", getLogsDir().toAbsolutePath().toString());
        }
    }
}
