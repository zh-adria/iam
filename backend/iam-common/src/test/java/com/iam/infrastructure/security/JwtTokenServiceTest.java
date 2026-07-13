package com.iam.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtTokenServiceTest {
    @Test
    void configuredRsaKeypairSignsAndPublishesCurrentAndPreviousJwks() throws Exception {
        KeyPair current = generateRsa();
        KeyPair previous = generateRsa();
        JwtTokenService jwt = new JwtTokenService(
                "iam-test",
                30,
                7,
                "kid-current",
                privatePem(current),
                publicPem(current),
                "kid-previous=" + publicPem(previous));
        jwt.init();

        String token = jwt.issueAccess(1L, "admin", "default", List.of("ROLE_ADMIN"), List.of("iam:test"), "client");

        assertEquals("admin", jwt.parse(token).getSubject());
        assertEquals(2, jwt.jwks().size());
        assertNotNull(jwt.jwks().stream()
                .filter(jwk -> "kid-current".equals(jwk.get("kid")))
                .findFirst()
                .orElse(null));
        assertNotNull(jwt.jwks().stream()
                .filter(jwk -> "kid-previous".equals(jwk.get("kid")))
                .findFirst()
                .orElse(null));
    }

    private KeyPair generateRsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String privatePem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    private String publicPem(KeyPair keyPair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
