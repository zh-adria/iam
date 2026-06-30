package com.iam.infrastructure.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * TOTP (RFC 6238) service — compatible with Google Authenticator / Microsoft Authenticator.
 */
@Slf4j
@Component
public class TotpService {

    private final TimeBasedOneTimePasswordGenerator totp;
    private final int digits;
    private final String issuer;

    public TotpService(@Value("${iam.mfa.totp-digits:6}") int digits,
                       @Value("${iam.mfa.totp-period:30}") int period,
                       @Value("${iam.mfa.totp-issuer:IAM-Platform}") String issuer) throws NoSuchAlgorithmException {
        this.digits = digits;
        this.issuer = issuer;
        // ponytail: java-otp defaults to SHA1+6 digits matching Google Authenticator — leave it.
        this.totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(period), digits,
                TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA1);
    }

    public String generateSecret() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(totp.getAlgorithm());
            kg.init(160);
            Key key = kg.generateKey();
            return Base64.getEncoder().withoutPadding().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String otpAuthUri(String secret, String account) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, account, secret, issuer, digits, totp.getTimeStep().getSeconds());
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.length() != digits) return false;
        try {
            Key key = new SecretKeySpec(Base64.getDecoder().decode(secret), totp.getAlgorithm());
            String current = totp.generateOneTimePasswordString(key, java.time.Instant.now());
            return constantTimeEq(current, code);
        } catch (Exception e) {
            log.warn("TOTP verify failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean constantTimeEq(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
