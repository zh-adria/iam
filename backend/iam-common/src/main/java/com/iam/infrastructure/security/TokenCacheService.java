package com.iam.infrastructure.security;

import com.iam.infrastructure.entity.AuditLogEntity;
import com.iam.infrastructure.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Cache + rate-limit + audit in one place.
 * ponytail: single RedisTemplate for token cache, login rate-limit, and idempotency keys.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCacheService {

    private final StringRedisTemplate redis;
    private final AuditLogRepository auditRepo;

    @Value("${iam.ratelimit.login-per-minute:10}")
    private int loginPerMinute;

    private static final String ACCESS_KEY = "iam:access:";
    private static final String JTI_KEY = "iam:jti:";
    private static final String FAIL_KEY = "iam:fail:";
    private static final String LOCK_KEY = "iam:lock:";
    private static final String SMS_KEY = "iam:sms:";
    private static final String MAGIC_KEY = "iam:magic:";

    public void cacheAccess(String jti, String subject, long ttlSec) {
        redis.opsForValue().set(ACCESS_KEY + jti, subject, Duration.ofSeconds(ttlSec));
    }

    public boolean isAccessValid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(ACCESS_KEY + jti));
    }

    public boolean isAccessRevoked(String jti) {
        return !isAccessValid(jti);
    }

    public void revokeAccess(String jti) {
        redis.delete(ACCESS_KEY + jti);
    }

    public boolean tryAcquireLogin(String ip) {
        String key = FAIL_KEY + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, 1, TimeUnit.MINUTES);
        return count != null && count <= loginPerMinute;
    }

    public void lockUser(Long userId, int minutes) {
        redis.opsForValue().set(LOCK_KEY + userId, "1", Duration.ofMinutes(minutes));
    }

    public boolean isLocked(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(LOCK_KEY + userId));
    }

    // ---------- SMS code ----------
    public void cacheSmsCode(String phone, String code, Duration ttl) {
        redis.opsForValue().set(SMS_KEY + phone, code, ttl);
    }
    public boolean verifySmsCode(String phone, String code) {
        String stored = redis.opsForValue().get(SMS_KEY + phone);
        return code.equals(stored);
    }
    public void consumeSmsCode(String phone) { redis.delete(SMS_KEY + phone); }

    // ---------- Magic link token ----------
    public void cacheMagicToken(String token, Long userId, Duration ttl) {
        redis.opsForValue().set(MAGIC_KEY + token, String.valueOf(userId), ttl);
    }
    public Long consumeMagicToken(String token) {
        String v = redis.opsForValue().get(MAGIC_KEY + token);
        if (v == null) return null;
        redis.delete(MAGIC_KEY + token);
        try { return Long.valueOf(v); } catch (Exception e) { return null; }
    }

    public void audit(Long userId, String tenant, String action, String result,
                      String principal, String ip, String detail) {
        AuditLogEntity prev = auditRepo.findTopByOrderByIdDesc();
        String prevHash = prev == null ? "GENESIS" : hash(prev.getId() + ":" + prev.getHashChainPrev());
        AuditLogEntity row = AuditLogEntity.builder()
                .userId(userId).tenantCode(tenant).action(action).result(result)
                .principal(principal).ip(ip).detail(detail)
                .hashChainPrev(prevHash).build();
        auditRepo.save(row);
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return new String(Hex.encode(md.digest(s.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
