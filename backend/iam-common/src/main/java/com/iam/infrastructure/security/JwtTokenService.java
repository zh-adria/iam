package com.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenService {
    private final String issuer;
    private final long accessTtlSec;
    private final long refreshTtlSec;
    private final String configuredKid;
    private final String privateKeyPem;
    private final String publicKeyPem;
    private final String previousPublicKeys;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String kid;
    private final Map<String, RSAPublicKey> verificationKeys = new HashMap<>();

    public JwtTokenService(@Value("${iam.jwt.issuer}") String issuer,
                           @Value("${iam.jwt.access-ttl-minutes:30}") int accessMin,
                           @Value("${iam.jwt.refresh-ttl-days:7}") int refreshDays,
                           @Value("${iam.jwt.key-id:}") String configuredKid,
                           @Value("${iam.jwt.rsa-private-key-pem:}") String privateKeyPem,
                           @Value("${iam.jwt.rsa-public-key-pem:}") String publicKeyPem,
                           @Value("${iam.jwt.previous-public-keys:}") String previousPublicKeys) {
        this.issuer = issuer;
        this.accessTtlSec = accessMin * 60L;
        this.refreshTtlSec = refreshDays * 86400L;
        this.configuredKid = configuredKid;
        this.privateKeyPem = privateKeyPem;
        this.publicKeyPem = publicKeyPem;
        this.previousPublicKeys = previousPublicKeys;
    }

    @PostConstruct
    public void init() {
        try {
            if (hasText(privateKeyPem) && hasText(publicKeyPem)) {
                privateKey = parsePrivateKey(privateKeyPem);
                publicKey = parsePublicKey(publicKeyPem);
                kid = hasText(configuredKid) ? configuredKid : thumbprint(publicKey);
                log.info("JwtTokenService initialized with configured RSA keypair (kid={})", kid);
            } else {
                initDeterministicDevKey();
            }
            verificationKeys.put(kid, publicKey);
            loadPreviousPublicKeys();
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
        return issueAccessWithScopes(jti, userId, username, tenantCode, roles, permissions, permissions, clientId);
    }

    public String issueAccessWithScopes(Long userId, String username, String tenantCode,
                                        List<String> roles, List<String> permissions,
                                        List<String> scopes, String clientId) {
        return issueAccessWithScopes(UUID.randomUUID().toString(), userId, username, tenantCode,
                roles, permissions, scopes, clientId);
    }

    public String issueAccessWithScopes(String jti, Long userId, String username, String tenantCode,
                                        List<String> roles, List<String> permissions,
                                        List<String> scopes, String clientId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("sub", username);
        claims.put("tenant", tenantCode);
        claims.put("roles", roles);
        claims.put("perms", permissions);
        claims.put("scope", String.join(" ", scopes == null ? permissions : scopes));
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
                               String email, String preferredUsername, String tenant,
                               Map<String, Object> extraClaims) {
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
        if (extraClaims != null) claims.putAll(extraClaims);
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
        return Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        String tokenKid = header.getKeyId();
                        RSAPublicKey key = tokenKid == null ? publicKey : verificationKeys.get(tokenKid);
                        if (key == null) {
                            throw new IllegalArgumentException("Unknown JWT kid: " + tokenKid);
                        }
                        return key;
                    }
                })
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long accessTtlSec() {
        return accessTtlSec;
    }

    public long refreshTtlSec() {
        return refreshTtlSec;
    }

    public String issuer() {
        return issuer;
    }

    public Map<String, Object> jwk() {
        return jwk(kid, publicKey);
    }

    public List<Map<String, Object>> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        verificationKeys.forEach((keyId, key) -> keys.add(jwk(keyId, key)));
        return keys;
    }

    private void initDeterministicDevKey() throws Exception {
        byte[] seed = issuer.getBytes(StandardCharsets.UTF_8);
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, sr);
        KeyPair kp = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        publicKey = (RSAPublicKey) kp.getPublic();
        kid = hasText(configuredKid) ? configuredKid : UUID.nameUUIDFromBytes(seed).toString().substring(0, 8);
        log.warn("JwtTokenService using deterministic dev RSA keypair (kid={}). Configure iam.jwt.rsa-private-key-pem and iam.jwt.rsa-public-key-pem for production.", kid);
    }

    private void loadPreviousPublicKeys() throws Exception {
        if (!hasText(previousPublicKeys)) return;
        for (String entry : previousPublicKeys.split(";;")) {
            if (!hasText(entry)) continue;
            int sep = entry.indexOf('=');
            if (sep <= 0 || sep == entry.length() - 1) {
                throw new IllegalArgumentException("Invalid iam.jwt.previous-public-keys entry. Expected kid=PEM");
            }
            String previousKid = entry.substring(0, sep).trim();
            verificationKeys.put(previousKid, parsePublicKey(entry.substring(sep + 1)));
        }
        log.info("Loaded {} JWT verification key(s), including previous keys", verificationKeys.size());
    }

    private Map<String, Object> jwk(String keyId, RSAPublicKey key) {
        if (key == null) return Map.of();
        byte[] n = key.getModulus().toByteArray();
        if (n.length > 1 && n[0] == 0) n = java.util.Arrays.copyOfRange(n, 1, n.length);
        String nB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(n);
        String eB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getPublicExponent().toByteArray());
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", nB64);
        jwk.put("e", eB64);
        return jwk;
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String stripPem(String pem) {
        return pem
                .replace("\\n", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    private String thumbprint(RSAPublicKey key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 12);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
