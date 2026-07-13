package com.iam.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IamJwtVerifier {
    private final String issuer;
    private final URI jwksUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private Map<String, RSAPublicKey> cachedKeys = Map.of();
    private Instant cacheExpiresAt = Instant.EPOCH;

    public IamJwtVerifier(String issuer, String jwksUri) {
        this(issuer, URI.create(jwksUri), HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofMinutes(10));
    }

    public IamJwtVerifier(String issuer, URI jwksUri, HttpClient httpClient,
                          ObjectMapper objectMapper, Duration cacheTtl) {
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public IamPrincipal verify(String token) {
        try {
            String kid = readKid(token);
            RSAPublicKey key = keyFor(kid);
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (!"access".equals(claims.get("typ", String.class))) {
                throw new IamSdkException("Token typ is not access");
            }
            return IamPrincipal.fromClaims(claims);
        } catch (IamSdkException e) {
            throw e;
        } catch (Exception e) {
            throw new IamSdkException("Invalid IAM access token", e);
        }
    }

    private RSAPublicKey keyFor(String kid) {
        Map<String, RSAPublicKey> keys = cachedKeys;
        if (Instant.now().isAfter(cacheExpiresAt) || !keys.containsKey(kid)) {
            keys = fetchKeys();
            cachedKeys = keys;
            cacheExpiresAt = Instant.now().plus(cacheTtl);
        }
        RSAPublicKey key = keys.get(kid);
        if (key == null) {
            throw new IamSdkException("No JWKS key found for kid: " + kid);
        }
        return key;
    }

    private Map<String, RSAPublicKey> fetchKeys() {
        HttpRequest request = HttpRequest.newBuilder(jwksUri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IamSdkException("JWKS request failed: HTTP " + response.statusCode());
            }
            Map<String, Object> jwks = objectMapper.readValue(response.body(), new TypeReference<>() {});
            List<Map<String, Object>> rawKeys = (List<Map<String, Object>>) jwks.get("keys");
            Map<String, RSAPublicKey> keys = new HashMap<>();
            if (rawKeys != null) {
                for (Map<String, Object> rawKey : rawKeys) {
                    if ("RSA".equals(rawKey.get("kty"))) {
                        keys.put((String) rawKey.get("kid"), toPublicKey(rawKey));
                    }
                }
            }
            return keys;
        } catch (IOException e) {
            throw new IamSdkException("Failed to parse JWKS response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IamSdkException("JWKS request interrupted", e);
        }
    }

    private RSAPublicKey toPublicKey(Map<String, Object> jwk) {
        try {
            byte[] n = Base64.getUrlDecoder().decode((String) jwk.get("n"));
            byte[] e = Base64.getUrlDecoder().decode((String) jwk.get("e"));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IamSdkException("Invalid RSA JWK", ex);
        }
    }

    private String readKid(String token) throws IOException {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IamSdkException("Malformed JWT");
        }
        byte[] headerJson = Base64.getUrlDecoder().decode(parts[0]);
        Map<String, Object> header = objectMapper.readValue(headerJson, new TypeReference<>() {});
        Object kid = header.get("kid");
        if (!(kid instanceof String) || ((String) kid).isBlank()) {
            throw new IamSdkException("JWT kid is missing");
        }
        return (String) kid;
    }
}
