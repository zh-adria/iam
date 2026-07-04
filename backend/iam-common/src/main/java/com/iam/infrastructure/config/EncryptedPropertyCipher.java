package com.iam.infrastructure.config;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class EncryptedPropertyCipher {

    private static final String PREFIX = "ENC(";
    private static final String FORMAT_PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EncryptedPropertyCipher() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(")");
    }

    public static String encrypt(String plainText, String key) {
        requireKey(key);
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + FORMAT_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(cipherText)
                    + ")";
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to encrypt property value", e);
        }
    }

    public static String decrypt(String encryptedValue, String key) {
        requireKey(key);
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }
        String payload = encryptedValue.substring(PREFIX.length(), encryptedValue.length() - 1);
        if (!payload.startsWith(FORMAT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported encrypted property format");
        }
        String[] parts = payload.substring(FORMAT_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted property format");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(key), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalArgumentException("Unable to decrypt property value: invalid key or ciphertext", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to decrypt property value", e);
        }
    }

    private static SecretKeySpec keySpec(String key) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(digest.digest(key.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Encrypted property key is required");
        }
    }
}
