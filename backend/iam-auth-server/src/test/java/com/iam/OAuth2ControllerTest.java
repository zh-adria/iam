package com.iam;

import com.iam.adapter.controller.OAuth2Controller;
import com.iam.app.service.OAuth2AuthService;
import com.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2ControllerTest {

    @Test
    void discovery_returnsAbsoluteIssuer() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/iam/oauth/.well-known/openid-configuration");
        req.setServerName("localhost");
        req.setServerPort(8080);
        req.setScheme("http");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        JwtTokenService jwt = org.mockito.Mockito.mock(JwtTokenService.class);
        org.mockito.Mockito.when(jwt.issuer()).thenReturn("http://localhost:8080/iam/oauth");
        OAuth2Controller ctrl = new OAuth2Controller(
                org.mockito.Mockito.mock(OAuth2AuthService.class),
                jwt);
        Map<String, Object> result = ctrl.discovery(req);
        String issuer = (String) result.get("issuer");
        assertNotNull(issuer);
        assertTrue(issuer.startsWith("http://") || issuer.startsWith("https://"),
                "issuer must be absolute URL, got: " + issuer);
    }
}
