package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.config.DynamicConfig;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.magiclink.MagicSender;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import com.iam.infrastructure.security.TokenCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Magic Link auth: email a one-time token, exchange for JWT.
 * Sender is pluggable via MagicSender interface (stub / smtp).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MagicLinkService {

    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final TokenCacheService cache;
    private final AuditLogService audit;
    private final DynamicConfig dynamicConfig;
    private final List<MagicSender> magicSenders;

    @Value("${iam.magic-link.base-url:http://localhost:5173/magic-callback}") private String baseUrl;
    @Value("${iam.magic-link.ttl-minutes:15}") private long ttlMin;

    public void send(String email, String ip) {
        UserEntity u = userRepo.findByEmail(email)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "邮箱未注册"));
        String token = UUID.randomUUID().toString().replace("-", "");
        long ttl = ttlMinutes();
        cache.cacheMagicToken(token, u.getId(), Duration.ofMinutes(ttl));
        String link = baseUrl() + "?token=" + token;
        magicSender().send(email, link, (int) ttl);
        audit.record(u.getId(), u.getTenantCode(), "MAGIC_LINK_SEND", "SUCCESS", u.getUsername(), ip, email);
    }

    @Transactional
    public TokenResponse verify(String token, String ip) {
        Long userId = cache.consumeMagicToken(token);
        if (userId == null) throw new AuthException("INVALID_MAGIC", "链接无效或已使用");
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getTenantCode(),
                Collections.emptyList(), Collections.emptyList(), "magic");
        audit.record(u.getId(), u.getTenantCode(), "MAGIC_LINK_LOGIN", "SUCCESS", u.getUsername(), ip, null);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .build();
    }

    private String baseUrl() {
        return dynamicConfig.getString("iam.magic-link.base-url", baseUrl);
    }

    private long ttlMinutes() {
        return dynamicConfig.getLong("iam.magic-link.ttl-minutes", ttlMin);
    }

    private MagicSender magicSender() {
        String provider = dynamicConfig.getString("iam.magic-link.provider", "stub");
        return magicSenders.stream()
                .filter(sender -> sender.providerName().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new AuthException("MAGIC_PROVIDER_NOT_FOUND", "Magic Link 服务商未配置: " + provider));
    }
}
