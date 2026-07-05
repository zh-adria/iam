package com.iam.infrastructure.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Standalone encryption utility for IAM configuration values.
 *
 * <p>Usage:
 * <pre>
 *   java -cp &lt;classpath&gt; com.iam.infrastructure.config.ConfigEncryptTool &lt;key&gt; &lt;plaintext&gt;
 * </pre>
 *
 * <p>Uses the same AES-256-GCM format as {@link EncryptedPropertyCipher},
 * producing values like {@code ENC(v1:&lt;base64_iv&gt;:&lt;base64_ciphertext&gt;)}.
 */
public final class ConfigEncryptTool {

    private static final String PREFIX = "ENC(";
    private static final String FORMAT_PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ConfigEncryptTool() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ConfigEncryptTool <configKey> <plaintext>");
            System.err.println("  Encrypts a plaintext value using AES-256-GCM.");
            System.err.println("  The configKey is the same key used in IAM_CONFIG_KEY / iam.config-key.");
            System.exit(1);
        }
        String key = args[0];
        String plaintext = args[1];
        System.out.println(encrypt(plaintext, key));
    }

    static String encrypt(String plainText, String key) {
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
            throw new IllegalArgumentException("Unable to encrypt value", e);
        }
    }

    static String decrypt(String encryptedValue, String key) {
        return EncryptedPropertyCipher.decrypt(encryptedValue, key);
    }

    private static SecretKeySpec keySpec(String key) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(digest.digest(key.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Config key is required (non-empty)");
        }
    }
}
