package com.iam;

import com.iam.app.dto.LoginCommand;
import com.iam.app.dto.TokenResponse;
import com.iam.app.service.AuthAppService;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.TokenCacheService;
import com.iam.infrastructure.security.TotpService;
import com.iam.start.DemoSeeder;
import com.iam.start.IamAuthServerApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = IamAuthServerApplication.class)
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired AuthAppService auth;
    @Autowired TotpService totp;
    @Autowired DemoSeeder seeder;
    @Autowired UserRepository userRepo;
    @MockBean TokenCacheService cache;

    @BeforeEach void seed() {
        when(cache.tryAcquireLogin(any())).thenReturn(true);
        when(cache.isLocked(any())).thenReturn(false);
        seeder.run();
        // reset admin state between tests — previous test may have locked the account
        userRepo.findByUsernameAndTenantCode("admin", "default").ifPresent(u -> {
            u.setStatus(1);
            u.setFailCount(0);
            u.setLockedUntil(null);
            userRepo.save(u);
        });
    }

    @Test
    void passwordLogin_thenMe() {
        LoginCommand cmd = LoginCommand.builder()
                .username("admin").password("Iam@2026")
                .tenantCode("default").clientId("iam-self")
                .grantType("password").ip("127.0.0.1").build();
        TokenResponse r = auth.login(cmd);
        assertNotNull(r.getAccessToken());
        assertFalse(r.isMfaRequired());
        assertEquals(30 * 60L, r.getExpiresIn());
    }

    @Test
    void badPassword_fails_and_rates_locks() {
        for (int i = 0; i < 6; i++) {
            try {
                auth.login(LoginCommand.builder()
                        .username("admin").password("wrong")
                        .tenantCode("default").clientId("iam-self")
                        .grantType("password").ip("127.0.0.1").build());
            } catch (Exception ignored) {}
        }
        // after 5 fails account locked
        Exception ex = assertThrows(Exception.class, () -> auth.login(LoginCommand.builder()
                .username("admin").password("Iam@2026")
                .tenantCode("default").clientId("iam-self")
                .grantType("password").ip("127.0.0.1").build()));
        assertTrue(ex.getMessage().contains("锁定"));
    }
}
