package com.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT service with RS256 (RSA keypair).
 *
 * Default: deterministic keypair derived from the issuer name so that auth-server
 * and admin-server (two JVMs) share the same keypair without extra config.
 * Override with iam.jwt.rsa-private-key-pem / iam.jwt.rsa-public-key-pem for prod.
 */
@Slf4j
@Component
public class JwtTokenService {

    private final String issuer;
    private final long accessTtlSec;
    private final long refreshTtlSec;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String kid;

    public JwtTokenService(@Value("${iam.jwt.issuer}") String issuer,
                           @Value("${iam.jwt.access-ttl-minutes:30}") int accessMin,
                           @Value("${iam.jwt.refresh-ttl-days:7}") int refreshDays) {
        this.issuer = issuer;
        this.accessTtlSec = accessMin * 60L;
        this.refreshTtlSec = refreshDays * 86400L;
    }

    @PostConstruct
    public void init() {
        try {
            // Deterministic keypair from issuer name as SecureRandom seed — same keypair
            // every startup, same across all services sharing the same issuer.
            byte[] seed = issuer.getBytes(StandardCharsets.UTF_8);
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(seed);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, sr);
            KeyPair kp = gen.generateKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey = (RSAPublicKey) kp.getPublic();
            this.kid = UUID.nameUUIDFromBytes(seed).toString().substring(0, 8);
            log.info("JwtTokenService initialized with deterministic RSA-2048 keypair (kid={}), derived from issuer '{}'", kid, issuer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT keypair", e);
        }
    }

    public String issueAccess(Long userId, String username, String tenantCode,
                              List<String> roles, List<String> permissions, String clientId) {
        return issueAccess(UUID.randomUUID().toString(), userId, username, tenantCode, roles, permissions, clientId);
    }

    public String issueAccess(String jti, Long userId, String username, String tenantCode,
                              List<String> roles, List<String> permissions, String clientId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("sub", username);
        claims.put("tenant", tenantCode);
        claims.put("roles", roles);
        claims.put("perms", permissions);
        claims.put("cid", clientId == null ? "iam-self" : clientId);
        claims.put("typ", "access");
        claims.put("jti", jti);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtlSec)))
                .setHeaderParam("kid", kid)
                .signWith(privateKey)
                .compact();
    }

    public String issueIdToken(Long userId, String subject, String clientId, String nonce,
                               String email, String preferredUsername, String tenant) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("aud", clientId);
        claims.put("iss", issuer);
        claims.put("uid", userId);
        claims.put("typ", "id_token");
        if (nonce != null) claims.put("nonce", nonce);
        if (email != null) claims.put("email", email);
        if (preferredUsername != null) claims.put("preferred_username", preferredUsername);
        if (tenant != null) claims.put("tenant", tenant);
        claims.put("jti", UUID.randomUUID().toString());
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(clientId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtlSec)))
                .setHeaderParam("kid", kid)
                .signWith(privateKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(publicKey).requireIssuer(issuer).build().parseClaimsJws(token).getBody();
    }

    public long accessTtlSec() { return accessTtlSec; }
    public long refreshTtlSec() { return refreshTtlSec; }
    public String issuer() { return issuer; }

    /**
     * RFC 7517 JWK representation of the public key, for the /jwks endpoint.
     */
    public Map<String, Object> jwk() {
        if (publicKey == null) return Map.of();
        byte[] n = publicKey.getModulus().toByteArray();
        if (n.length > 1 && n[0] == 0) n = java.util.Arrays.copyOfRange(n, 1, n.length);
        String nB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(n);
        String eB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray());
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", nB64);
        jwk.put("e", eB64);
        return jwk;
    }
}
