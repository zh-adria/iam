package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.social.SocialAuthGateway;
import com.iam.app.service.social.SocialProfile;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialLoginServiceTest {

    private SocialBindingRepository socialRepo;
    private UserRepository userRepo;
    private JwtTokenService jwt;
    private AuditLogService audit;
    private SocialAuthGateway gateway;
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        socialRepo = mock(SocialBindingRepository.class);
        userRepo = mock(UserRepository.class);
        jwt = mock(JwtTokenService.class);
        audit = mock(AuditLogService.class);
        gateway = mock(SocialAuthGateway.class);
        service = new SocialLoginService(socialRepo, userRepo, jwt, audit, gateway);
    }

    @Test
    void authorizeUrlDelegatesToGateway() {
        when(gateway.authorizeUrl("qq")).thenReturn("https://graph.qq.com/oauth2.0/authorize");

        assertEquals("https://graph.qq.com/oauth2.0/authorize", service.authorizeUrl("qq"));
    }

    @Test
    void callbackProvisionsUserAndIssuesJwtForNewSocialProfile() {
        when(gateway.fetchProfile("qq", "code-1", null))
                .thenReturn(new SocialProfile("qq", "openid-1", "QQ User", "qq@example.com"));
        when(socialRepo.findByProviderAndProviderUserId("qq", "openid-1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity user = inv.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(jwt.issueAccess(eq(42L), startsWith("qq_"), eq("default"), anyList(), anyList(), eq("qq")))
                .thenReturn("access-token");
        when(jwt.accessTtlSec()).thenReturn(1800L);

        TokenResponse response = service.callback("qq", "code-1", "127.0.0.1");

        assertEquals("access-token", response.getAccessToken());
        verify(socialRepo).save(argThat((SocialBindingEntity binding) ->
                binding.getProvider().equals("qq")
                        && binding.getProviderUserId().equals("openid-1")
                        && binding.getProviderUsername().equals("QQ User")));
        verify(audit).record(eq(42L), eq("default"), eq("SOCIAL_LOGIN"), eq("SUCCESS"),
                startsWith("qq_"), eq("127.0.0.1"), contains("provider=qq openid=openid-1"));
    }
}
