package com.iam.app.service;

import com.iam.infrastructure.entity.ApiKeyEntity;
import com.iam.infrastructure.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API Key lifecycle: create, validate, revoke, list.
 *
 * Key format: {prefix}_{32 hex chars}. Prefix identifies the key ("live"/"test"/"service").
 * Only the prefix and a SHA-256 hash are persisted; the raw key is returned once at creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepo;
    private static final SecureRandom RNG = new SecureRandom();

    private static final Set<String> VALID_PREFIXES = Set.of("live", "test", "service");

    /**
     * Create a new API key.
     *
     * @return ApiResult containing prefix, rawKey (shown once), scope, expiresAt, name, owner
     */
    @Transactional
    public Map<String, Object> createKey(String prefix, String name, String owner,
                                         String tenantCode, String scope, long ttlSeconds) {
        if (prefix == null || !VALID_PREFIXES.contains(prefix)) {
            throw new IllegalArgumentException("prefix must be one of: " + VALID_PREFIXES);
        }
        String rawKey = generateRawKey(prefix);
        String keyHash = sha256(rawKey);

        if (apiKeyRepo.existsByKeyHash(keyHash)) {
            throw new IllegalStateException("key collision (extremely unlikely)");
        }

        ApiKeyEntity e = ApiKeyEntity.builder()
                .prefix(prefix)
                .keyHash(keyHash)
                .name(name)
                .owner(owner == null ? "unknown" : owner)
                .tenantCode(tenantCode == null ? "default" : tenantCode)
                .scope(scope == null ? "read" : scope)
                .enabled(true)
                .expiresAt(Instant.now().getEpochSecond() + ttlSeconds > 0
                        ? Instant.ofEpochMilli(System.currentTimeMillis() + ttlSeconds * 1000)
                        : Instant.ofEpochMilli(Long.MAX_VALUE))
                .build();
        apiKeyRepo.save(e);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prefix", prefix);
        result.put("key", rawKey);            // shown once
        result.put("scope", e.getScope());
        result.put("expiresAt", e.getExpiresAt().toString());
        result.put("name", name);
        result.put("owner", owner);
        return result;
    }

    @Transactional
    public void revokeKey(Long id) {
        ApiKeyEntity e = apiKeyRepo.findById(id).orElseThrow(() ->
                new NoSuchElementException("API key not found"));
        e.setEnabled(false);
        apiKeyRepo.save(e);
    }

    @Transactional
    public void revokeKeyByPrefix(String prefix) {
        apiKeyRepo.findByPrefix(prefix).ifPresent(k -> {
            k.setEnabled(false);
            apiKeyRepo.save(k);
        });
    }

    public List<Map<String, Object>> listKeys() {
        return apiKeyRepo.findAll().stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    /**
     * Validate a raw key. Returns the entity if valid, null otherwise.
     */
    public ApiKeyEntity validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        String hash = sha256(rawKey);
        return apiKeyRepo.findByKeyHash(hash)
                .filter(ApiKeyEntity::getEnabled)
                .filter(k -> k.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }

    private Map<String, Object> toRow(ApiKeyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("prefix", e.getPrefix());
        m.put("name", e.getName());
        m.put("owner", e.getOwner());
        m.put("tenantCode", e.getTenantCode());
        m.put("scope", e.getScope());
        m.put("enabled", e.getEnabled());
        m.put("expiresAt", e.getExpiresAt().toString());
        m.put("createdAt", e.getCreatedAt().toString());
        m.put("lastUsedAt", e.getLastUsedAt().toString());
        return m;
    }

    private String generateRawKey(String prefix) {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        String hex = bytesToHex(bytes);
        return prefix + "_" + hex;
    }

    private static String sha256(String s) {
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
