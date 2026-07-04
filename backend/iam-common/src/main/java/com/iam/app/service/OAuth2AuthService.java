package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.OAuth2ClientEntity;
import com.iam.infrastructure.entity.RefreshTokenEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.OAuth2ClientRepository;
import com.iam.infrastructure.repository.RefreshTokenRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuthCodeStore;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import com.iam.infrastructure.security.PasswordHasher;
import com.iam.infrastructure.security.TokenCacheService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OAuth2 Authorization Server: authorization_code (with PKCE), refresh_token, password, client_credentials.
 * OIDC: issues id_token when scope includes openid. RFC 7662 introspect + RFC 7009 revoke.
 * RS256 — JwtTokenService handles keypair; this service delegates signing to it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthService {

    private final OAuth2ClientRepository clientRepo;
    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final AuthCodeStore codeStore;
    private final JwtTokenService jwt;
    private final PasswordHasher hasher;
    private final AuditLogService audit;
    private final TokenCacheService cache;

    @Transactional
    public String authorize(String clientId, String redirectUri, String scope, String state,
                            String codeChallenge, String codeChallengeMethod, String nonce,
                            Long userId, String username, String tenantCode) {
        OAuth2ClientEntity c = validateClient(clientId, null, "authorization_code", redirectUri);
        String scopes = intersectScopes(c.getScopes(), scope);
        String code = codeStore.issue(userId, clientId, redirectUri, scopes,
                codeChallenge, codeChallengeMethod, nonce);
        audit.record(userId, tenantCode, "OAUTH_AUTHORIZE", "SUCCESS", username, null,
                "client=" + clientId + " scope=" + scopes + (codeChallenge != null ? " pkce=S256" : ""));
        return code;
    }

    @Transactional
    public Map<String, Object> token(String grantType, String clientId, String clientSecret,
                                     String code, String redirectUri, String codeVerifier,
                                     String refreshToken,
                                     String username, String password,
                                     String scope) {
        TokenResponse r;
        switch (grantType) {
            case "authorization_code": r = authCodeGrant(clientId, clientSecret, code, redirectUri, codeVerifier); break;
            case "refresh_token":      r = refreshGrant(clientId, clientSecret, refreshToken); break;
            case "password":           r = passwordGrant(clientId, clientSecret, username, password, scope); break;
            case "client_credentials": r = clientCredentialsGrant(clientId, clientSecret, scope); break;
            default: throw new AuthException("UNSUPPORTED_GRANT", "不支持的 grant_type: " + grantType);
        }
        return toTokenMap(r);
    }

    public Map<String, Object> userInfo(Long userId) {
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        Map<String, Object> r = new HashMap<>();
        r.put("sub", String.valueOf(u.getId()));
        r.put("preferred_username", u.getUsername());
        r.put("email", u.getEmail());
        r.put("phone", u.getPhone());
        r.put("tenant", u.getTenantCode());
        return r;
    }

    /** RFC 7662 introspection. */
    public Map<String, Object> introspect(String token, String clientId, String clientSecret) {
        if (clientId != null) validateClient(clientId, clientSecret, null, null);
        Map<String, Object> r = new HashMap<>();
        r.put("active", false);
        if (token == null || token.isEmpty()) return r;
        // try refresh token first
        var rt = refreshRepo.findByToken(token).orElse(null);
        if (rt != null) {
            boolean active = rt.getRevokedAt() == null && rt.getExpiresAt().isAfter(Instant.now());
            r.put("active", active);
            if (active) {
                r.put("client_id", rt.getClientId());
                r.put("scope", rt.getScopes());
                r.put("token_type", "refresh_token");
                r.put("user_id", String.valueOf(rt.getUserId()));
                r.put("exp", rt.getExpiresAt().getEpochSecond());
            }
            return r;
        }
        try {
            Claims c = jwt.parse(token);
            String jti = (String) c.get("jti");
            boolean revoked = jti != null && cache.isAccessRevoked(jti);
            r.put("active", !revoked);
            if (!revoked) {
                r.put("client_id", c.get("cid"));
                r.put("sub", c.getSubject());
                r.put("scope", c.get("perms"));
                r.put("exp", c.getExpiration().getTime() / 1000);
                r.put("uid", c.get("uid"));
                r.put("tenant", c.get("tenant"));
            }
        } catch (Exception e) {
            // inactive
        }
        return r;
    }

    /** RFC 7009 revocation. */
    @Transactional
    public void revoke(String token, String clientId, String clientSecret) {
        if (clientId != null) validateClient(clientId, clientSecret, null, null);
        var rt = refreshRepo.findByToken(token).orElse(null);
        if (rt != null) {
            rt.setRevokedAt(Instant.now());
            refreshRepo.save(rt);
            return;
        }
        try {
            Claims c = jwt.parse(token);
            String jti = (String) c.get("jti");
            if (jti != null) cache.revokeAccess(jti);
        } catch (Exception ignored) { }
    }

    // ---------- grant handlers ----------

    private TokenResponse authCodeGrant(String clientId, String clientSecret, String code, String redirectUri, String codeVerifier) {
        OAuth2ClientEntity c = validateClient(clientId, clientSecret, "authorization_code", redirectUri);
        AuthCodeStore.Entry e = codeStore.consume(code);
        if (e == null) throw new AuthException("INVALID_CODE", "授权码无效或已使用");
        if (!e.getClientId().equals(clientId)) throw new AuthException("CLIENT_MISMATCH", "客户端不匹配");
        if (redirectUri != null && !redirectUri.equals(e.getRedirectUri()))
            throw new AuthException("REDIRECT_MISMATCH", "redirect_uri 不匹配");
        // PKCE verification (S256 only; plain deprecated)
        if (e.getCodeChallenge() != null) {
            if (codeVerifier == null) throw new AuthException("PKCE_REQUIRED", "缺少 code_verifier");
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(codeVerifier));
            if (!expected.equals(e.getCodeChallenge()))
                throw new AuthException("PKCE_FAILED", "code_verifier 校验失败");
        }
        UserEntity u = userRepo.findById(e.getUserId())
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        return issueTokenPair(u, c.getClientId(), e.getScopes(), e.getNonce(), true);
    }

    private TokenResponse refreshGrant(String clientId, String clientSecret, String refreshToken) {
        validateClient(clientId, clientSecret, "refresh_token", null);
        RefreshTokenEntity rt = refreshRepo.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException("INVALID_REFRESH", "刷新令牌无效"));
        if (!rt.getClientId().equals(clientId))
            throw new AuthException("CLIENT_MISMATCH", "refresh_token 不属于该客户端");
        if (rt.getRevokedAt() != null || rt.getExpiresAt().isBefore(Instant.now()))
            throw new AuthException("EXPIRED_REFRESH", "刷新令牌已失效");
        rt.setRevokedAt(Instant.now());
        refreshRepo.save(rt); // rotate
        UserEntity u = userRepo.findById(rt.getUserId())
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        return issueTokenPair(u, clientId, rt.getScopes(), null, false);
    }

    private TokenResponse passwordGrant(String clientId, String clientSecret, String username, String password, String scope) {
        OAuth2ClientEntity c = validateClient(clientId, clientSecret, "password", null);
        // ponytail: password grant deprecated by OAuth2.1 — kept for legacy clients.
        UserEntity u = userRepo.findByUsernameAndTenantCode(username, "default")
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        if (!hasher.matches(password, u.getPasswordHash()))
            throw new AuthException("BAD_CREDENTIALS", "用户名或密码错误");
        String scopes = intersectScopes(c.getScopes(), scope);
        return issueTokenPair(u, c.getClientId(), scopes, null, false);
    }

    private TokenResponse clientCredentialsGrant(String clientId, String clientSecret, String scope) {
        OAuth2ClientEntity c = validateClient(clientId, clientSecret, "client_credentials", null);
        String scopes = intersectScopes(c.getScopes(), scope);
        List<String> scopeList = scopes == null || scopes.isEmpty() ? Collections.emptyList() : Arrays.asList(scopes.split(","));
        String access = jwt.issueAccess(0L, "client:" + clientId, "system",
                Collections.emptyList(), scopeList, clientId);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .permissions(scopeList)
                .build();
    }

    // ---------- helpers ----------

    private TokenResponse issueTokenPair(UserEntity u, String clientId, String scopes, String nonce, boolean includeIdToken) {
        List<String> scopeList = scopes == null || scopes.isEmpty() ? Collections.emptyList() : Arrays.asList(scopes.split(","));
        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getTenantCode(),
                Collections.emptyList(), scopeList, clientId);
        String refreshToken = java.util.UUID.randomUUID().toString().replace("-", "") + "." + Long.toHexString(u.getId());
        RefreshTokenEntity rt = RefreshTokenEntity.builder()
                .token(refreshToken).userId(u.getId()).tenantCode(u.getTenantCode())
                .clientId(clientId).scopes(scopes == null ? "" : scopes)
                .expiresAt(Instant.now().plusSeconds(jwt.refreshTtlSec()))
                .build();
        refreshRepo.save(rt);
        audit.record(u.getId(), u.getTenantCode(), "OAUTH_TOKEN", "SUCCESS", u.getUsername(), null,
                "client=" + clientId + " scope=" + scopes);

        TokenResponse tr = TokenResponse.builder()
                .accessToken(access).refreshToken(refreshToken).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .permissions(scopeList)
                .build();
        // OIDC id_token when openid scope present
        if (includeIdToken && scopes != null && scopes.contains("openid")) {
            tr.setIdToken(jwt.issueIdToken(u.getId(), u.getUsername(), clientId, nonce,
                    u.getEmail(), u.getUsername(), u.getTenantCode()));
        }
        return tr;
    }

    private Map<String, Object> toTokenMap(TokenResponse r) {
        Map<String, Object> m = new HashMap<>();
        m.put("access_token", r.getAccessToken());
        m.put("token_type", r.getTokenType() == null ? "Bearer" : r.getTokenType());
        m.put("expires_in", r.getExpiresIn());
        if (r.getRefreshToken() != null) m.put("refresh_token", r.getRefreshToken());
        if (r.getIdToken() != null) m.put("id_token", r.getIdToken());
        if (r.getPermissions() != null) m.put("scope", String.join(" ", r.getPermissions()));
        return m;
    }

    private OAuth2ClientEntity validateClient(String clientId, String clientSecret, String grantType, String redirectUri) {
        OAuth2ClientEntity c = clientRepo.findById(clientId)
                .orElseThrow(() -> new AuthException("CLIENT_NOT_FOUND", "客户端不存在"));
        if (clientSecret != null && !hasher.matches(clientSecret, c.getClientSecretHash()))
            throw new AuthException("BAD_CLIENT_SECRET", "客户端密钥错误");
        if (grantType != null) {
            Set<String> grants = new HashSet<>(Arrays.asList(c.getGrantTypes().split(",")));
            if (!grants.contains(grantType))
                throw new AuthException("GRANT_NOT_ALLOWED", "客户端未授权该 grant_type");
        }
        if (redirectUri != null && c.getRedirectUris() != null) {
            List<String> allowed = Arrays.asList(c.getRedirectUris().split(","));
            if (!allowed.contains(redirectUri))
                throw new AuthException("REDIRECT_NOT_ALLOWED", "redirect_uri 不在白名单");
        }
        return c;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String intersectScopes(String clientScopes, String requested) {
        if (requested == null || requested.isEmpty()) return clientScopes;
        Set<String> allowed = new HashSet<>(Arrays.asList(clientScopes.split(",")));
        Set<String> want = new HashSet<>(Arrays.asList(requested.split("[ ,]")));
        want.retainAll(allowed);
        return String.join(",", want);
    }

}
