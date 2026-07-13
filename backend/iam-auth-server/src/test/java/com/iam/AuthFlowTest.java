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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IamAuthServerApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired AuthAppService auth;
    @Autowired TotpService totp;
    @Autowired DemoSeeder seeder;
    @Autowired UserRepository userRepo;
    @Autowired MockMvc mockMvc;
    @MockBean TokenCacheService cache;

    @BeforeEach void seed() {
        when(cache.tryAcquireLogin(any())).thenReturn(true);
        when(cache.isLocked(any())).thenReturn(false);
        when(cache.isAccessValid(any())).thenReturn(true);
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
    void passwordLogin_setsBrowserCookieForOauth2Navigation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Iam@2026\",\"tenantCode\":\"default\",\"clientId\":\"iam-self\",\"grantType\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("IAM_ACCESS_TOKEN=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    void loginOptions_returnsConfigurableLoginMethods() throws Exception {
        mockMvc.perform(get("/api/auth/login-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.methods[0]").value("password"))
                .andExpect(jsonPath("$.data.methods[1]").value("sms"))
                .andExpect(jsonPath("$.data.socialProviders[0]").value("wechat"));
    }

    @Test
    void oauth2AuthorizeAcceptsLoginCookie() throws Exception {
        LoginCommand cmd = LoginCommand.builder()
                .username("admin").password("Iam@2026")
                .tenantCode("default").clientId("iam-self")
                .grantType("password").ip("127.0.0.1").build();
        TokenResponse token = auth.login(cmd);

        mockMvc.perform(get("/oauth/authorize")
                        .cookie(new Cookie("IAM_ACCESS_TOKEN", token.getAccessToken()))
                        .param("response_type", "code")
                        .param("client_id", "demo-client")
                        .param("redirect_uri", "http://localhost:5173/callback")
                        .param("scope", "openid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://localhost:5173/callback?code=")));
    }

    @Test
    void authzCheck_usesCurrentBearerPermissions() throws Exception {
        LoginCommand cmd = LoginCommand.builder()
                .username("admin").password("Iam@2026")
                .tenantCode("default").clientId("iam-self")
                .grantType("password").ip("127.0.0.1").build();
        TokenResponse token = auth.login(cmd);

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"iam:role:create\",\"target\":{\"tenantCode\":\"default\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.permission").value("iam:role:create"))
                .andExpect(jsonPath("$.data.sub").value("admin"));
    }

    @Test
    void authzCheck_deniesMissingPermission() throws Exception {
        LoginCommand cmd = LoginCommand.builder()
                .username("alice").password("User@2026")
                .tenantCode("default").clientId("iam-self")
                .grantType("password").ip("127.0.0.1").build();
        TokenResponse token = auth.login(cmd);

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"iam:role:create\",\"target\":{\"tenantCode\":\"default\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false));
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
