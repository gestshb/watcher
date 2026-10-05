package com.watcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigEncryptorTest {

    @Test
    public void testHashAndVerifyPassword() {
        char[] password = "SecretMasterPassword123!".toCharArray();
        String[] saltAndHash = ConfigEncryptor.hashPassword(password);

        assertNotNull(saltAndHash[0]);
        assertNotNull(saltAndHash[1]);

        assertTrue(ConfigEncryptor.verifyPassword(password, saltAndHash[0], saltAndHash[1]));
        assertFalse(ConfigEncryptor.verifyPassword("WrongPassword".toCharArray(), saltAndHash[0], saltAndHash[1]));
    }

    @Test
    public void testEncryptAndDecrypt() throws Exception {
        char[] password = "AdminPassword456".toCharArray();
        String originalMessage = "capture.interval.minutes=5\ndrive.folder.name=KidsStudy";
        byte[] plaintext = originalMessage.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = ConfigEncryptor.encrypt(plaintext, password);
        assertNotNull(encrypted);
        assertNotEquals(new String(encrypted, StandardCharsets.UTF_8), originalMessage);

        byte[] decrypted = ConfigEncryptor.decrypt(encrypted, password);
        assertEquals(originalMessage, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    public void testDecryptWithWrongPasswordFails() throws Exception {
        char[] password = "CorrectPassword".toCharArray();
        char[] wrongPassword = "WrongPassword".toCharArray();
        byte[] plaintext = "Sensitive configuration text".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = ConfigEncryptor.encrypt(plaintext, password);
        assertThrows(Exception.class, () -> ConfigEncryptor.decrypt(encrypted, wrongPassword));
    }

    @Test
    public void testConfigManagerSaveAndLoad(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.dat");
        ConfigManager manager = new ConfigManager(configFile);

        char[] password = "TestPassword_2026".toCharArray();
        AppConfig config = new AppConfig();
        config.setCaptureIntervalMinutes(3);
        config.setImageQuality(0.8f);
        config.setS3BucketName("my-test-bucket");
        config.setS3Region("eu-central-1");
        config.setS3Endpoint("https://r2.cloudflarestorage.com");

        manager.save(config, password);

        AppConfig loaded = manager.load(password);
        assertEquals(3, loaded.getCaptureIntervalMinutes());
        assertEquals(0.8f, loaded.getImageQuality(), 0.001);
        assertEquals("my-test-bucket", loaded.getS3BucketName());
        assertEquals("eu-central-1", loaded.getS3Region());
        assertEquals("https://r2.cloudflarestorage.com", loaded.getS3Endpoint());
        assertTrue(ConfigEncryptor.verifyPassword(password, loaded.getAdminPasswordSalt(), loaded.getAdminPasswordHash()));
    }
}
