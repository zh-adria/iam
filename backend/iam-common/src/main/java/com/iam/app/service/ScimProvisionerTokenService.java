package com.iam.app.service;

import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.ScimTokenEntity;
import com.iam.infrastructure.repository.ScimTokenRepository;
import com.iam.infrastructure.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCIM provisioner token management — generate, validate, revoke tokens for external provisioning systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScimProvisionerTokenService {

    private final ScimTokenRepository scimTokenRepo;
    private final PasswordHasher hasher;

    private static final SecureRandom RNG = new SecureRandom();

    /** Generate a new SCIM provisioner token. Returns the raw token (shown once). */
    @Transactional
    public ScimTokenEntity createToken(String name, String tenantCode, String scope, long ttlDays) {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String prefix = rawToken.substring(0, 8);
        String hash = hasher.encode(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(ttlDays * 86400L);
        ScimTokenEntity e = ScimTokenEntity.builder()
                .name(name)
                .tokenHash(hash)
                .tokenPrefix(prefix)
                .tenantCode(tenantCode)
                .scope(scope)
                .expiresAt(expiresAt)
                .build();
        scimTokenRepo.save(e);
        // attach raw token for one-time display (not persisted)
        e.setTokenHash(rawToken);
        return e;
    }

    /** Validate a raw bearer token against stored hashes. */
    public boolean validate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) return false;
        String prefix = rawToken.length() >= 8 ? rawToken.substring(0, 8) : rawToken;
        List<ScimTokenEntity> candidates = scimTokenRepo.findByEnabledTrue();
        for (ScimTokenEntity e : candidates) {
            if (prefix.equals(e.getTokenPrefix()) && hasher.matches(rawToken, e.getTokenHash())) {
                if (e.getExpiresAt().isBefore(Instant.now())) return false;
                e.setLastUsedAt(Instant.now());
                scimTokenRepo.save(e);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void revoke(Long id) {
        scimTokenRepo.findById(id).ifPresent(e -> {
            e.setEnabled(false);
            scimTokenRepo.save(e);
        });
    }

    public List<Map<String, Object>> listAll() {
        return scimTokenRepo.findAll().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", e.getId());
                    m.put("name", e.getName());
                    m.put("tokenPrefix", e.getTokenPrefix());
                    m.put("tenantCode", e.getTenantCode());
                    m.put("scope", e.getScope());
                    m.put("enabled", e.getEnabled());
                    m.put("expiresAt", e.getExpiresAt());
                    m.put("lastUsedAt", e.getLastUsedAt());
                    m.put("createdAt", e.getCreatedAt());
                    return m;
                }).collect(Collectors.toList());
    }
}
