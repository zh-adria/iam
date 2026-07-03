package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified social login handler for WeChat / Alipay / QQ / DingTalk / WeCom.
 * Flow: callback with code → exchange for provider access_token → fetch userinfo → find-or-provision local user → issue JWT.
 * ponytail: shared RestTemplate + per-provider endpoint map. Real impl needs IdP-specific signature/encryption
 * (Alipay RSA2, WeCom corp-secret flow). Stubs where marked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialBindingRepository socialRepo;
    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;
    private final RestTemplate http = new RestTemplate();

    @Value("${iam.social.wechat.app-id:}") private String wechatId;
    @Value("${iam.social.wechat.app-secret:}") private String wechatSecret;
    @Value("${iam.social.dingtalk.app-id:}") private String dingId;
    @Value("${iam.social.dingtalk.app-secret:}") private String dingSecret;
    @Value("${iam.social.qq.app-id:}") private String qqId;
    @Value("${iam.social.qq.app-key:}") private String qqKey;
    @Value("${iam.social.alipay.app-id:}") private String alipayId;
    @Value("${iam.social.alipay.app-private-key:}") private String alipayKey;
    @Value("${iam.social.alipay.alipay-public-key:}") private String alipayPublicKey;
    @Value("${iam.social.wecom.corp-id:}") private String wecomCorpId;
    @Value("${iam.social.wecom.corp-secret:}") private String wecomSecret;

    /** Returns authorization redirect URL for the provider. */
    public String authorizeUrl(String provider) {
        switch (provider) {
            case "wechat":
                return "https://open.weixin.qq.com/connect/qrconnect?appid=" + wechatId
                        + "&redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/wechat/callback")
                        + "&response_type=code&scope=snsapi_login#wechat_redirect";
            case "alipay":
                return "https://openauth.alipay.com/oauth2/publicAppAuthorize.htm?app_id=" + alipayId
                        + "&scope=auth_user&redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/alipay/callback");
            case "qq":
                return "https://graph.qq.com/oauth2.0/authorize?client_id=" + qqId
                        + "&redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/qq/callback")
                        + "&response_type=code&scope=get_user_info";
            case "dingtalk":
                return "https://login.dingtalk.com/oauth2/auth?redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/dingtalk/callback")
                        + "&response_type=code&client_id=" + dingId + "&scope=openid&prompt=consent";
            case "wecom":
                return "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + wecomCorpId
                        + "&redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/wecom/callback")
                        + "&response_type=code&scope=snsapi_base";
            default: throw new AuthException("UNKNOWN_PROVIDER", "不支持的社交 provider: " + provider);
        }
    }

    @Transactional
    public TokenResponse callback(String provider, String code, String ip) {
        Map<String, Object> profile = fetchProfile(provider, code);
        String openId = String.valueOf(profile.get("openid"));
        if (openId == null || openId.isEmpty() || "null".equals(openId)) {
            throw new AuthException("SOCIAL_FAIL", provider + " 未返回 openid");
        }
        String displayName = (String) profile.getOrDefault("name", provider + "_user");
        String email = (String) profile.get("email");

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

    /** Exchange code for access_token then fetch userinfo. */
    private Map<String, Object> fetchProfile(String provider, String code) {
        // ponytail: per-provider HTTP dance. Real Alipay needs RSA2 signing of params; WeCom needs
        // corp-access_token flow first. Stubs return a synthesized openid from the code so the flow wires up.
        switch (provider) {
            case "wechat": {
                if (wechatId.isEmpty()) throw new AuthException("SOCIAL_NOT_CONFIGURED", "wechat 未配置");
                String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + wechatId
                        + "&secret=" + wechatSecret + "&code=" + code + "&grant_type=authorization_code";
                Map<?, ?> t = http.getForObject(tokenUrl, Map.class);
                if (t == null || t.get("openid") == null)
                    throw new AuthException("SOCIAL_FAIL", "wechat token exchange failed");
                Map<String, Object> p = new HashMap<>();
                p.put("openid", t.get("openid"));
                p.put("name", "wechat_user");
                return p;
            }
            case "qq": {
                if (qqId.isEmpty()) throw new AuthException("SOCIAL_NOT_CONFIGURED", "qq 未配置");
                String tokenUrl = "https://graph.qq.com/oauth2.0/token?grant_type=authorization_code"
                        + "&client_id=" + qqId + "&client_secret=" + qqKey + "&code=" + code
                        + "&redirect_uri=" + enc("http://localhost:8080/iam/api/auth/social/qq/callback");
                String body = http.getForObject(tokenUrl, String.class);
                String accessToken = parseFormToken(body);
                String openidUrl = "https://graph.qq.com/oauth2.0/me?access_token=" + accessToken;
                String oidBody = http.getForObject(openidUrl, String.class);
                String openid = parseJsonpOpenid(oidBody);
                Map<String, Object> p = new HashMap<>();
                p.put("openid", openid);
                p.put("name", "qq_user");
                return p;
            }
            case "dingtalk": {
                if (dingId.isEmpty()) throw new AuthException("SOCIAL_NOT_CONFIGURED", "dingtalk 未配置");
                // ponytail: DingTalk OIDC flow — exchange code for user info via /v2.0/oauth2/userinfo
                String userInfoUrl = "https://api.dingtalk.com/v2.0/oauth2/userInfo?access_token=" + getDingtalkAccessToken();
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.APPLICATION_JSON);
                Map<String, String> req = new HashMap<>();
                req.put("code", code);
                Map<?, ?> r = http.postForObject(userInfoUrl, new HttpEntity<>(req, h), Map.class);
                Map<String, Object> p = new HashMap<>();
                p.put("openid", r == null ? null : r.get("openid"));
                p.put("name", "dingtalk_user");
                return p;
            }
            case "wecom": {
                if (wecomCorpId.isEmpty()) throw new AuthException("SOCIAL_NOT_CONFIGURED", "wecom 未配置");
                String tokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + wecomCorpId + "&corpsecret=" + wecomSecret;
                Map<?, ?> t = http.getForObject(tokenUrl, Map.class);
                String at = t == null ? null : (String) t.get("access_token");
                String userUrl = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=" + at + "&code=" + code;
                Map<?, ?> u = http.getForObject(userUrl, Map.class);
                Map<String, Object> p = new HashMap<>();
                p.put("openid", u == null ? null : u.get("userid"));
                p.put("name", "wecom_user");
                return p;
            }
            case "alipay": {
                if (alipayId.isEmpty()) throw new AuthException("SOCIAL_NOT_CONFIGURED", "alipay 未配置");
                // Alipay OAuth2: exchange authorization_code → access_token → user_id via
                // alipay.system.oauth.token API signed with RSA2.
                //
                // Uses reflection to load Alipay SDK classes so the platform compiles without
                // the alipay-sdk dependency at compile time; activate by setting
                // iam.social.alipay.app-id / app-private-key / alipay-public-key.
                try {
                    Class<?> clientClass = Class.forName("com.alipay.api.DefaultAlipayClient");
                    Object client = clientClass.getConstructor(String.class, String.class,
                            String.class, String.class, String.class, String.class, String.class)
                            .newInstance("https://openapi.alipay.com/gateway.do",
                                    alipayId, alipayKey, "json", "UTF-8",
                                    alipayPublicKey, "RSA2");
                    Class<?> reqClass = Class.forName("com.alipay.api.request.AlipaySystemOAuthTokenRequest");
                    Object req = reqClass.getDeclaredConstructor().newInstance();
                    reqClass.getMethod("setGrantType", String.class).invoke(req, "authorization_code");
                    reqClass.getMethod("setCode", String.class).invoke(req, code);
                    Object resp = clientClass.getMethod("execute", Class.forName("com.alipay.api.AlipayRequest"))
                            .invoke(client, req);
                    boolean success = (Boolean) resp.getClass().getMethod("isSuccess").invoke(resp);
                    if (success) {
                        Object userId = resp.getClass().getMethod("getUserId").invoke(resp);
                        if (userId != null) {
                            Map<String, Object> p = new HashMap<>();
                            p.put("openid", String.valueOf(userId));
                            Object nickName = resp.getClass().getMethod("getNickName").invoke(resp);
                            p.put("name", nickName == null ? "alipay_user" : nickName);
                            return p;
                        }
                    }
                    Object subMsg = null;
                    try { subMsg = resp.getClass().getMethod("getSubMsg").invoke(resp); } catch (Exception ignored) {}
                    throw new AuthException("SOCIAL_FAIL", "alipay token exchange failed: " + subMsg);
                } catch (AuthException e) {
                    throw e;
                } catch (ClassNotFoundException e) {
                    throw new AuthException("SOCIAL_NOT_CONFIGURED",
                            "alipay SDK 不在 classpath，请确认 alipay-sdk-java 依赖已引入");
                } catch (Exception e) {
                    throw new AuthException("SOCIAL_FAIL", "alipay 调用失败: " + e.getMessage());
                }
            }
            default:
                throw new AuthException("UNKNOWN_PROVIDER", "不支持的 provider: " + provider);
        }
    }

    private String getDingtalkAccessToken() {
        String url = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> req = new HashMap<>();
        req.put("appKey", dingId);
        req.put("appSecret", dingSecret);
        Map<?, ?> r = http.postForObject(url, new HttpEntity<>(req, h), Map.class);
        return r == null ? null : (String) r.get("accessToken");
    }

    private static String parseFormToken(String body) {
        if (body == null) return null;
        for (String kv : body.split("&")) {
            if (kv.startsWith("access_token=")) return kv.substring("access_token=".length());
        }
        return null;
    }

    private static String parseJsonpOpenid(String body) {
        if (body == null) return null;
        int i = body.indexOf("\"openid\":\"");
        if (i < 0) return null;
        int start = i + "\"openid\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
