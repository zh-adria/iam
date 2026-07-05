package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.social.SocialAuthGateway;
import com.iam.app.service.social.SocialProfile;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Unified social login handler for WeChat / Alipay / QQ / DingTalk / WeCom.
 * Flow: callback with code -> JustAuth profile -> find-or-provision local user -> issue JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialBindingRepository socialRepo;
    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;
    private final SocialAuthGateway socialAuthGateway;

    /** Returns authorization redirect URL for the provider. */
    public String authorizeUrl(String provider) {
        return socialAuthGateway.authorizeUrl(provider);
    }

    @Transactional
    public TokenResponse callback(String provider, String code, String ip) {
        SocialProfile profile = socialAuthGateway.fetchProfile(provider, code, null);
        String openId = profile.getProviderUserId();
        if (openId == null || openId.isEmpty() || "null".equals(openId)) {
            throw new AuthException("SOCIAL_FAIL", provider + " 未返回 openid");
        }
        String displayName = profile.getDisplayName();
        String email = profile.getEmail();

        UserEntity user = socialRepo.findByProviderAndProviderUserId(provider, openId)
                .flatMap(b -> userRepo.findById(b.getUserId()))
                .orElseGet(() -> provision(provider, openId, displayName, email));

        String access = jwt.issueAccess(user.getId(), user.getUsername(), user.getTenantCode(),
                Collections.emptyList(), Collections.emptyList(), provider);
        audit.record(user.getId(), user.getTenantCode(), "SOCIAL_LOGIN", "SUCCESS", user.getUsername(), ip,
                "provider=" + provider + " openid=" + openId);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .build();
    }

    private UserEntity provision(String provider, String openId, String displayName, String email) {
        UserEntity u = userRepo.save(UserEntity.builder()
                .username(provider + "_" + openId.substring(0, Math.min(openId.length(), 12)))
                .passwordHash("SOCIAL_DISABLED")
                .email(email)
                .tenantCode("default")
                .status(1).mfaEnabled(false).failCount(0)
                .build());
        socialRepo.save(SocialBindingEntity.builder()
                .userId(u.getId())
                .provider(provider)
                .providerUserId(openId)
                .providerUsername(displayName)
                .build());
        return u;
    }
}
