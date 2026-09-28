package com.watcher.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Handles AES-256-GCM encryption and PBKDF2WithHmacSHA256 key derivation.
 */
public class ConfigEncryptor {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Hashes a password with a random salt using PBKDF2WithHmacSHA256.
     *
     * @param password raw password
     * @return [base64Salt, base64Hash]
     */
    public static String[] hashPassword(char[] password) {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH);
        return new String[]{
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        };
    }

    /**
     * Verifies a password against an existing salt and hash.
     */
    public static boolean verifyPassword(char[] password, String base64Salt, String base64ExpectedHash) {
        if (base64Salt == null || base64ExpectedHash == null) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(base64Salt);
        byte[] expectedHash = Base64.getDecoder().decode(base64ExpectedHash);
        byte[] actualHash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH);

        if (actualHash.length != expectedHash.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < actualHash.length; i++) {
            diff |= actualHash[i] ^ expectedHash[i];
        }
        return diff == 0;
    }

    /**
     * Encrypts plaintext data using AES-256-GCM derived from a password.
     * Format: [16 bytes Salt][12 bytes IV][Ciphertext + 16 bytes Auth Tag]
     */
    public static byte[] encrypt(byte[] plaintext, char[] password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        SecretKey secretKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);
        return byteBuffer.array();
    }

    /**
     * Decrypts AES-256-GCM encrypted data using a password.
     */
    public static byte[] decrypt(byte[] payload, char[] password) throws Exception {
        if (payload.length < SALT_LENGTH + GCM_IV_LENGTH + 16) {
            throw new IllegalArgumentException("Encrypted payload too short");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
        byte[] salt = new byte[SALT_LENGTH];
        byteBuffer.get(salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);

        byte[] ciphertext = new byte[byteBuffer.remaining()];
        byteBuffer.get(ciphertext);

        SecretKey secretKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        return cipher.doFinal(ciphertext);
    }

    private static SecretKey deriveKey(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 derivation failed", e);
        }
    }
}
