package com.watcher.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Loads and saves configuration in encrypted format (config.dat) or fallback plain properties.
 */
public class ConfigManager {
    private static final Logger logger = LogManager.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = "config.dat";
    private static final String PLAIN_CONFIG_FILE = "config.properties";

    private final Path configPath;

    public ConfigManager() {
        this(Path.of(DEFAULT_CONFIG_FILE));
    }

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    /**
     * Loads AppConfig. If an encrypted file exists, password is required.
     */
    public AppConfig load(char[] password) throws Exception {
        if (Files.exists(configPath)) {
            byte[] encryptedBytes = Files.readAllBytes(configPath);
            if (password == null || password.length == 0) {
                throw new IllegalArgumentException("Password required to load encrypted configuration.");
            }
            byte[] decrypted = ConfigEncryptor.decrypt(encryptedBytes, password);
            return deserializeProperties(decrypted);
        }

        // Check fallback to plain properties if unencrypted exists (e.g. initial setup)
        Path plainPath = Path.of(PLAIN_CONFIG_FILE);
        if (Files.exists(plainPath)) {
            logger.warn("Found unencrypted config.properties. Loading and will encrypt on save.");
            byte[] plainBytes = Files.readAllBytes(plainPath);
            return deserializeProperties(plainBytes);
        }

        logger.info("No config file found. Initializing default configuration.");
        return new AppConfig();
    }

    /**
     * Saves AppConfig encrypted with the specified password.
     */
    public void save(AppConfig config, char[] password) throws Exception {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty when saving encrypted configuration.");
        }

        // Set or update hash
        String[] saltAndHash = ConfigEncryptor.hashPassword(password);
        config.setAdminPasswordSalt(saltAndHash[0]);
        config.setAdminPasswordHash(saltAndHash[1]);

        byte[] serialized = serializeProperties(config);
        byte[] encrypted = ConfigEncryptor.encrypt(serialized, password);

        // Atomic write via temp file
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), "cfg", ".tmp");
        Files.write(tempFile, encrypted);
        Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        logger.info("Encrypted configuration saved to: {}", configPath);
    }

    private byte[] serializeProperties(AppConfig config) throws IOException {
        Properties props = new Properties();
        props.setProperty("capture.interval.minutes", String.valueOf(config.getCaptureIntervalMinutes()));
        props.setProperty("capture.image.quality", String.valueOf(config.getImageQuality()));
        props.setProperty("capture.spool.dir", config.getSpoolDirectory());
        props.setProperty("capture.max.spool.bytes", String.valueOf(config.getMaxSpoolSizeBytes()));
        props.setProperty("capture.delete.after.upload", String.valueOf(config.isDeleteAfterUpload()));
        props.setProperty("drive.folder.name", config.getDriveFolderName());
        props.setProperty("drive.retention.days", String.valueOf(config.getRetentionDays()));
        props.setProperty("drive.credentials.path", config.getCredentialsPath());
        props.setProperty("admin.password.hash", config.getAdminPasswordHash());
        props.setProperty("admin.password.salt", config.getAdminPasswordSalt());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.store(baos, "Watcher Daemon Encrypted Configuration");
        return baos.toByteArray();
    }

    private AppConfig deserializeProperties(byte[] bytes) throws IOException {
        Properties props = new Properties();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            props.load(bais);
        }

        AppConfig config = new AppConfig();
        if (props.containsKey("capture.interval.minutes")) {
            config.setCaptureIntervalMinutes(Integer.parseInt(props.getProperty("capture.interval.minutes")));
        }
        if (props.containsKey("capture.image.quality")) {
            config.setImageQuality(Float.parseFloat(props.getProperty("capture.image.quality")));
        }
        if (props.containsKey("capture.spool.dir")) {
            config.setSpoolDirectory(props.getProperty("capture.spool.dir"));
        }
        if (props.containsKey("capture.max.spool.bytes")) {
            config.setMaxSpoolSizeBytes(Long.parseLong(props.getProperty("capture.max.spool.bytes")));
        }
        if (props.containsKey("capture.delete.after.upload")) {
            config.setDeleteAfterUpload(Boolean.parseBoolean(props.getProperty("capture.delete.after.upload")));
        }
        if (props.containsKey("drive.folder.name")) {
            config.setDriveFolderName(props.getProperty("drive.folder.name"));
        }
        if (props.containsKey("drive.retention.days")) {
            config.setRetentionDays(Integer.parseInt(props.getProperty("drive.retention.days")));
        }
        if (props.containsKey("drive.credentials.path")) {
            config.setCredentialsPath(props.getProperty("drive.credentials.path"));
        }
        if (props.containsKey("admin.password.hash")) {
            config.setAdminPasswordHash(props.getProperty("admin.password.hash"));
        }
        if (props.containsKey("admin.password.salt")) {
            config.setAdminPasswordSalt(props.getProperty("admin.password.salt"));
        }

        return config;
    }
}
