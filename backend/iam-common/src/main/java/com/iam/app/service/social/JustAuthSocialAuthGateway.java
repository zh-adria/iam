package com.iam.app.service.social;

import com.iam.domain.AuthException;
import com.iam.infrastructure.config.DynamicConfig;
import me.zhyd.oauth.AuthRequestBuilder;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class JustAuthSocialAuthGateway implements SocialAuthGateway {
    private static final Map<String, ProviderSpec> PROVIDERS;

    static {
        Map<String, ProviderSpec> providers = new HashMap<>();
        providers.put("wechat", new ProviderSpec("wechat_open", "wechat.app-id", "wechat.app-secret", null));
        providers.put("alipay", new ProviderSpec("alipay", "alipay.app-id", "alipay.app-private-key", null));
        providers.put("qq", new ProviderSpec("qq", "qq.app-id", "qq.app-key", null));
        providers.put("dingtalk", new ProviderSpec("dingtalk", "dingtalk.app-id", "dingtalk.app-secret", null));
        providers.put("wecom", new ProviderSpec("wechat_enterprise", "wecom.corp-id", "wecom.corp-secret", "wecom.agent-id"));
        PROVIDERS = Collections.unmodifiableMap(providers);
    }

    private final Environment env;
    private final DynamicConfig dynamicConfig;

    public JustAuthSocialAuthGateway(Environment env, DynamicConfig dynamicConfig) {
        this.env = env;
        this.dynamicConfig = dynamicConfig;
    }

    @Override
    public String authorizeUrl(String provider) {
        return request(provider).authorize(AuthStateUtils.createState());
    }

    @Override
    public SocialProfile fetchProfile(String provider, String code, String state) {
        AuthCallback callback = new AuthCallback();
        callback.setCode(code);
        callback.setState(state);
        AuthResponse<AuthUser> response = request(provider).login(callback);
        if (response == null || !response.ok() || response.getData() == null) {
            throw new AuthException("SOCIAL_FAIL", provider + " 授权登录失败");
        }
        AuthUser user = response.getData();
        String openId = user.getUuid();
        if (!StringUtils.hasText(openId)) {
            throw new AuthException("SOCIAL_FAIL", provider + " 未返回 openid");
        }
        String displayName = firstText(user.getNickname(), user.getUsername(), provider + "_user");
        return new SocialProfile(provider, openId, displayName, user.getEmail());
    }

    private AuthRequest request(String provider) {
        ProviderSpec spec = spec(provider);
        return AuthRequestBuilder.builder()
                .source(spec.source)
                .authConfig(config(provider, spec))
                .build();
    }

    private ProviderSpec spec(String provider) {
        ProviderSpec spec = PROVIDERS.get(provider);
        if (spec == null) {
            throw new AuthException("UNKNOWN_PROVIDER", "不支持的社交 provider: " + provider);
        }
        return spec;
    }

    private AuthConfig config(String provider, ProviderSpec spec) {
        String clientId = property(spec.clientIdKey);
        String clientSecret = property(spec.clientSecretKey);
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new AuthException("SOCIAL_NOT_CONFIGURED", provider + " 未配置");
        }
        AuthConfig.AuthConfigBuilder builder = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri(provider));
        if ("alipay".equals(provider)) {
            String publicKey = property("alipay.alipay-public-key");
            if (!StringUtils.hasText(publicKey)) {
                throw new AuthException("SOCIAL_NOT_CONFIGURED", provider + " 未配置");
            }
            builder.alipayPublicKey(publicKey);
        }
        if (spec.agentIdKey != null) {
            String agentId = property(spec.agentIdKey);
            if (StringUtils.hasText(agentId)) {
                builder.agentId(agentId);
            }
        }
        return builder.build();
    }

    private String redirectUri(String provider) {
        String specific = configValue(provider + ".redirect-uri");
        if (StringUtils.hasText(specific)) {
            return specific;
        }
        String base = configValue("redirect-base-url");
        if (!StringUtils.hasText(base)) {
            base = "http://localhost:8080/iam/api/auth/social";
        }
        return trimTrailingSlash(base) + "/" + provider + "/callback";
    }

    private String property(String relativeKey) {
        return configValue(relativeKey);
    }

    private String configValue(String relativeKey) {
        String key = "iam.social." + relativeKey;
        return dynamicConfig.getString(key, env.getProperty(key, ""));
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static class ProviderSpec {
        private final String source;
        private final String clientIdKey;
        private final String clientSecretKey;
        private final String agentIdKey;

        private ProviderSpec(String source, String clientIdKey, String clientSecretKey, String agentIdKey) {
            this.source = source;
            this.clientIdKey = clientIdKey;
            this.clientSecretKey = clientSecretKey;
            this.agentIdKey = agentIdKey;
        }
    }
}
