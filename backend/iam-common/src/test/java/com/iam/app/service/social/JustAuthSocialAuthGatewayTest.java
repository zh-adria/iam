package com.iam.app.service.social;

import com.iam.domain.AuthException;
import com.iam.infrastructure.config.DynamicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JustAuthSocialAuthGatewayTest {

    @Test
    void rejectsUnknownProvider() {
        JustAuthSocialAuthGateway gateway = gatewayWith();

        AuthException ex = assertThrows(AuthException.class,
                () -> gateway.authorizeUrl("github"));

        assertEquals("UNKNOWN_PROVIDER", ex.getCode());
    }

    @Test
    void rejectsUnconfiguredKnownProvider() {
        JustAuthSocialAuthGateway gateway = gatewayWith();

        AuthException ex = assertThrows(AuthException.class,
                () -> gateway.authorizeUrl("qq"));

        assertEquals("SOCIAL_NOT_CONFIGURED", ex.getCode());
    }

    @Test
    void buildsAuthorizeUrlForConfiguredProvider() {
        JustAuthSocialAuthGateway gateway = gatewayWith(
                "iam.social.qq.app-id", "qq-client",
                "iam.social.qq.app-key", "qq-secret");

        String url = gateway.authorizeUrl("qq");

        assertTrue(url.contains("client_id=qq-client"));
        assertTrue(url.contains("redirect_uri="));
    }

    @Test
    void buildsAuthorizeUrlsForCurrentProviders() {
        JustAuthSocialAuthGateway gateway = gatewayWith(
                "iam.social.wechat.app-id", "wechat-client",
                "iam.social.wechat.app-secret", "wechat-secret",
                "iam.social.alipay.app-id", "alipay-client",
                "iam.social.alipay.app-private-key", "alipay-private-key",
                "iam.social.alipay.alipay-public-key", "alipay-public-key",
                "iam.social.alipay.redirect-uri", "https://iam.example.com/api/auth/social/alipay/callback",
                "iam.social.qq.app-id", "qq-client",
                "iam.social.qq.app-key", "qq-secret",
                "iam.social.dingtalk.app-id", "dingtalk-client",
                "iam.social.dingtalk.app-secret", "dingtalk-secret",
                "iam.social.wecom.corp-id", "wecom-client",
                "iam.social.wecom.corp-secret", "wecom-secret",
                "iam.social.wecom.agent-id", "1000001");

        assertTrue(gateway.authorizeUrl("wechat").contains("wechat-client"));
        assertTrue(gateway.authorizeUrl("alipay").contains("alipay-client"));
        assertTrue(gateway.authorizeUrl("qq").contains("qq-client"));
        assertTrue(gateway.authorizeUrl("dingtalk").contains("dingtalk-client"));
        assertTrue(gateway.authorizeUrl("wecom").contains("wecom-client"));
    }

    @Test
    void dynamicConfigOverridesEnvironmentFallback() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("iam.social.redirect-base-url",
                        "http://localhost:8080/iam/api/auth/social")
                .withProperty("iam.social.qq.app-id", "env-client")
                .withProperty("iam.social.qq.app-key", "env-secret");
        DynamicConfig dynamicConfig = mock(DynamicConfig.class);
        when(dynamicConfig.getString("iam.social.qq.app-id", "env-client")).thenReturn("dynamic-client");
        when(dynamicConfig.getString("iam.social.qq.app-key", "env-secret")).thenReturn("dynamic-secret");
        when(dynamicConfig.getString("iam.social.redirect-base-url",
                "http://localhost:8080/iam/api/auth/social"))
                .thenReturn("http://localhost:8080/iam/api/auth/social");
        when(dynamicConfig.getString("iam.social.qq.redirect-uri", "")).thenReturn("");

        JustAuthSocialAuthGateway gateway = new JustAuthSocialAuthGateway(env, dynamicConfig);

        assertTrue(gateway.authorizeUrl("qq").contains("client_id=dynamic-client"));
    }

    private JustAuthSocialAuthGateway gatewayWith(String... properties) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("iam.social.redirect-base-url",
                        "http://localhost:8080/iam/api/auth/social");
        DynamicConfig dynamicConfig = mock(DynamicConfig.class);
        for (int i = 0; i < properties.length; i += 2) {
            env.withProperty(properties[i], properties[i + 1]);
        }
        when(dynamicConfig.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        return new JustAuthSocialAuthGateway(env, dynamicConfig);
    }
}
