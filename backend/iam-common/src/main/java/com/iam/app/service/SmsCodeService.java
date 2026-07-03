package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import com.iam.infrastructure.security.TokenCacheService;
import com.iam.infrastructure.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;

/**
 * SMS verification code: send + verify. Stored in Redis with TTL.
 * Sender is pluggable via SmsSender interface (stub / aliyun / tencent).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private final TokenCacheService cache;
    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;
    private final SmsSender smsSender;
    private final SecureRandom rng = new SecureRandom();

    @Value("${iam.sms.code-ttl-seconds:300}") private long ttlSec;
    @Value("${iam.sms.code-length:6}") private int codeLength;

    public void send(String phone) {
        if (phone == null || phone.length() < 7) throw new AuthException("BAD_PHONE", "手机号格式错误");
        String code = generate();
        cache.cacheSmsCode(phone, code, Duration.ofSeconds(ttlSec));
        try {
            smsSender.send(phone, code);
        } catch (Exception e) {
            log.error("SMS send failed via {}: {}", smsSender.providerName(), e.getMessage());
            throw new AuthException("SMS_SEND_FAILED", "短信发送失败");
        }
    }

    public boolean verify(String phone, String code) {
        if (code == null) return false;
        boolean ok = cache.verifySmsCode(phone, code);
        if (ok) cache.consumeSmsCode(phone);
        return ok;
    }

    @Transactional
    public TokenResponse loginOrProvision(String phone, String code, String ip) {
        if (!verify(phone, code)) throw new AuthException("SMS_FAIL", "验证码错误或已过期");
        UserEntity u = userRepo.findByPhone(phone)
                .orElseGet(() -> userRepo.save(UserEntity.builder()
                        .username("sms_" + phone)
                        .passwordHash("SMS_DISABLED")
                        .phone(phone).tenantCode("default")
                        .status(1).mfaEnabled(false).failCount(0).build()));
        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getTenantCode(),
                Collections.emptyList(), Collections.emptyList(), "sms");
        audit.record(u.getId(), u.getTenantCode(), "SMS_LOGIN", "SUCCESS", u.getUsername(), ip, phone);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .build();
    }

    private String generate() {
        int max = (int) Math.pow(10, codeLength);
        int n = rng.nextInt(max);
        return String.format("%0" + codeLength + "d", n);
    }
}
