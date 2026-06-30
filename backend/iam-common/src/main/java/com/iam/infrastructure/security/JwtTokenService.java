package com.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSec;
    private final long refreshTtlSec;

    public JwtTokenService(@Value("${iam.jwt.secret}") String secret,
                           @Value("${iam.jwt.issuer}") String issuer,
                           @Value("${iam.jwt.access-ttl-minutes:30}") int accessMin,
                           @Value("${iam.jwt.refresh-ttl-days:7}") int refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlSec = accessMin * 60L;
        this.refreshTtlSec = refreshDays * 86400L;
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
                .signWith(key)
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
        // ponytail: at_hash binding omitted — add when issuing paired access_token per OIDC spec 3.1.2.6
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(issuer)
                .setSubject(subject)
                .setAudience(clientId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtlSec)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).requireIssuer(issuer).build().parseClaimsJws(token).getBody();
    }

    public long accessTtlSec() { return accessTtlSec; }
    public long refreshTtlSec() { return refreshTtlSec; }
    public String issuer() { return issuer; }
}
