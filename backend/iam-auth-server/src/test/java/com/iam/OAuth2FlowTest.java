package com.iam;

import com.iam.app.service.OAuth2AuthService;
import com.iam.infrastructure.security.AuthCodeStore;
import com.iam.infrastructure.security.TokenCacheService;
import com.iam.start.DemoSeeder;
import com.iam.start.IamAuthServerApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = IamAuthServerApplication.class)
@ActiveProfiles("test")
class OAuth2FlowTest {

    @Autowired OAuth2AuthService oauth;
    @Autowired AuthCodeStore codeStore;
    @Autowired DemoSeeder seeder;
    @MockBean TokenCacheService cache;

    @BeforeEach void setup() {
        when(cache.tryAcquireLogin(any())).thenReturn(true);
        when(cache.isLocked(any())).thenReturn(false);
        when(cache.isAccessRevoked(any())).thenReturn(false);
        seeder.run();
    }

    @Test
    void authCodeFlow_issueCode_then_exchange_withIdToken() {
        Long userId = 1L;
        String code = oauth.authorize("demo-client", "http://localhost:5173/callback",
                "openid,profile", "xyz", null, "S256", "nonce123",
                userId, "admin", "default");
        assertNotNull(code);

        Map<String, Object> tok = oauth.token("authorization_code", "demo-client", "demo-secret",
                code, "http://localhost:5173/callback", null, null, null, null, null);
        assertNotNull(tok.get("access_token"));
        assertEquals(30 * 60L, ((Number) tok.get("expires_in")).longValue());
        assertNotNull(tok.get("id_token"), "OIDC id_token must be issued when scope includes openid");
    }

    @Test
    void pkce_wrongVerifier_rejected() {
        Long userId = 1L;
        String challenge = b64url(sha256("correct-verifier"));
        String code = oauth.authorize("demo-client", "http://localhost:5173/callback",
                "openid", "st", challenge, "S256", null, userId, "admin", "default");
        assertThrows(Exception.class, () -> oauth.token("authorization_code", "demo-client", "demo-secret",
                code, "http://localhost:5173/callback", "wrong-verifier", null, null, null, null));
    }

    @Test
    void pkce_correctVerifier_succeeds() {
        Long userId = 1L;
        String challenge = b64url(sha256("verifier-abc"));
        String code = oauth.authorize("demo-client", "http://localhost:5173/callback",
                "openid", "st", challenge, "S256", null, userId, "admin", "default");
        Map<String, Object> tok = oauth.token("authorization_code", "demo-client", "demo-secret",
                code, "http://localhost:5173/callback", "verifier-abc", null, null, null, null);
        assertNotNull(tok.get("access_token"));
    }

    @Test
    void clientCredentialsGrant_works() {
        Map<String, Object> tok = oauth.token("client_credentials", "demo-client", "demo-secret",
                null, null, null, null, null, null, "openid");
        assertNotNull(tok.get("access_token"));
    }

    @Test
    void badClientSecret_rejected() {
        assertThrows(Exception.class, () -> oauth.token("client_credentials", "demo-client", "wrong-secret",
                null, null, null, null, null, null, "openid"));
    }

    @Test
    void introspect_accessToken_active() {
        Map<String, Object> tok = oauth.token("client_credentials", "demo-client", "demo-secret",
                null, null, null, null, null, null, "openid");
        Map<String, Object> ins = oauth.introspect((String) tok.get("access_token"), null, null);
        assertEquals(true, ins.get("active"));
    }

    @Test
    void introspect_garbageToken_inactive() {
        Map<String, Object> ins = oauth.introspect("not-a-real-token", null, null);
        assertEquals(false, ins.get("active"));
    }

    private static byte[] sha256(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
