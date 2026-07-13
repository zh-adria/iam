package com.iam;

import com.iam.sdk.IamAuthClient;
import com.iam.sdk.IamAuthorizationCheckResponse;
import com.iam.sdk.IamAuthorizer;
import com.iam.sdk.IamJwtVerifier;
import com.iam.sdk.IamPrincipal;
import com.iam.sdk.IamTokenResponse;
import com.iam.infrastructure.security.TokenCacheService;
import com.iam.start.DemoSeeder;
import com.iam.start.IamAuthServerApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = IamAuthServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SdkHttpIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    DemoSeeder seeder;

    @MockBean
    TokenCacheService cache;

    @BeforeEach
    void setup() {
        when(cache.isAccessValid(any())).thenReturn(true);
        when(cache.isAccessRevoked(any())).thenReturn(false);
        seeder.run();
    }

    @Test
    void sdkFetchesTokenVerifiesJwksAndCallsAuthzCheckOverHttp() {
        String baseUrl = "http://localhost:" + port + "/iam";
        IamAuthClient client = new IamAuthClient(baseUrl);

        IamTokenResponse token = client.clientCredentials(
                "demo-client",
                "demo-secret",
                "iam:role:create");

        IamJwtVerifier verifier = new IamJwtVerifier(
                "iam-platform-test",
                baseUrl + "/oauth/jwks");
        IamPrincipal principal = verifier.verify(token.getAccessToken());

        assertEquals("client:demo-client", principal.getUsername());
        assertTrue(IamAuthorizer.hasPermission(principal, "iam:role:create"));

        IamAuthorizationCheckResponse check = client.checkPermission(
                token.getAccessToken(),
                "iam:role:create",
                Map.of("tenantCode", "default"));

        assertTrue(check.isAllowed());
        assertEquals("iam:role:create", check.getPermission());
    }
}
