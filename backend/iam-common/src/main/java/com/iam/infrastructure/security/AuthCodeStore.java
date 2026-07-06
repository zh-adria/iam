package com.iam.infrastructure.security;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authorization code store. Codes are short-lived (60s default).
 * ponytail: global map — fine for single instance. Upgrade: Redis SETEX when scaling.
 */
@Component
public class AuthCodeStore {

    private static final long CODE_TTL_SEC = 60;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Data
    @RequiredArgsConstructor
    public static class Entry {
        private final Long userId;
        private final String clientId;
        private final String redirectUri;
        private final String scopes;
        private final Instant issuedAt;
        private final String codeChallenge;
        private final String codeChallengeMethod;
        private final String nonce;
        private final String claims;
        private boolean used;
    }

    public String issue(Long userId, String clientId, String redirectUri, String scopes,
                        String codeChallenge, String codeChallengeMethod, String nonce,
                        String claims) {
        String code = UUID.randomUUID().toString().replace("-", "");
        store.put(code, new Entry(userId, clientId, redirectUri, scopes, Instant.now(),
                codeChallenge, codeChallengeMethod, nonce, claims));
        return code;
    }

    public Entry consume(String code) {
        Entry e = store.get(code);
        if (e == null) return null;
        if (e.isUsed()) return null;
        if (Instant.now().getEpochSecond() - e.getIssuedAt().getEpochSecond() > CODE_TTL_SEC) {
            store.remove(code);
            return null;
        }
        e.setUsed(true);
        store.remove(code);
        return e;
    }
}
