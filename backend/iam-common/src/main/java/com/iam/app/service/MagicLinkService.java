package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.UserEntity;
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
import java.util.UUID;

/**
 * Magic Link auth: email a one-time token, exchange for JWT.
 * ponytail: mailer is a stub (logs link). Wire JavaMail/SES in send() for prod.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MagicLinkService {

    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final TokenCacheService cache;
    private final AuditLogService audit;

    @Value("${iam.magic-link.base-url:http://localhost:5173/magic-callback}") private String baseUrl;
    @Value("${iam.magic-link.ttl-minutes:15}") private long ttlMin;

    public void send(String email, String ip) {
        UserEntity u = userRepo.findByEmail(email)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "邮箱未注册"));
        String token = UUID.randomUUID().toString().replace("-", "");
        cache.cacheMagicToken(token, u.getId(), Duration.ofMinutes(ttlMin));
        String link = baseUrl + "?token=" + token;
        // ponytail: stub mailer — wire SMTP here.
        log.info("[MAGIC-LINK-STUB] to {}: {} (ttl={}min)", email, link, ttlMin);
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
}
