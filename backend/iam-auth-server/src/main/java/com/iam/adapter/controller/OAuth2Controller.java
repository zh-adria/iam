package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.app.service.OAuth2AuthService;
import com.iam.domain.AuthException;
import com.iam.infrastructure.security.IamPrincipal;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2AuthService oauth;
    private final JwtTokenService jwt;

    /**
     * GET /oauth/authorize?response_type=code&client_id=...&redirect_uri=...&scope=...&state=...
     * Optional PKCE: code_challenge, code_challenge_method=S256
     * Optional OIDC: nonce
     */
    @GetMapping("/authorize")
    public void authorize(@RequestParam String response_type,
                          @RequestParam String client_id,
                          @RequestParam String redirect_uri,
                          @RequestParam(required = false) String scope,
                          @RequestParam(required = false) String state,
                          @RequestParam(required = false) String code_challenge,
                          @RequestParam(required = false, defaultValue = "S256") String code_challenge_method,
                          @RequestParam(required = false) String nonce,
                          @AuthenticationPrincipal IamPrincipal principal,
                          HttpServletResponse res) throws IOException {
        if (!"code".equals(response_type)) {
            res.sendError(400, "unsupported response_type");
            return;
        }
        if (code_challenge != null && !"S256".equals(code_challenge_method)) {
            res.sendError(400, "only S256 code_challenge_method supported");
            return;
        }
        if (principal == null || principal.getUserId() == null) {
            StringBuilder q = new StringBuilder("response_type=").append(response_type)
                    .append("&client_id=").append(client_id)
                    .append("&redirect_uri=").append(URLEncoder.encode(redirect_uri, StandardCharsets.UTF_8));
            if (scope != null) q.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
            if (state != null) q.append("&state=").append(state);
            if (code_challenge != null) {
                q.append("&code_challenge=").append(code_challenge)
                        .append("&code_challenge_method=").append(code_challenge_method);
            }
            if (nonce != null) q.append("&nonce=").append(URLEncoder.encode(nonce, StandardCharsets.UTF_8));
            String loginUrl = "/login?return_to=" + URLEncoder.encode("/iam/oauth/authorize?" + q, StandardCharsets.UTF_8);
            res.sendRedirect(loginUrl);
            return;
        }
        String code = oauth.authorize(client_id, redirect_uri, scope, state,
                code_challenge, code_challenge_method, nonce,
                principal.getUserId(), principal.getUsername(), principal.getTenantCode());
        StringBuilder sb = new StringBuilder(redirect_uri);
        sb.append(redirect_uri.contains("?") ? "&" : "?")
          .append("code=").append(code);
        if (state != null) sb.append("&state=").append(state);
        res.sendRedirect(sb.toString());
    }

    /**
     * POST /oauth/token  (form-urlencoded per RFC 6749)
     * grant_type=authorization_code: code, redirect_uri, code_verifier (PKCE)
     * grant_type=refresh_token: refresh_token
     * grant_type=password: username, password
     * grant_type=client_credentials: scope
     */
    @PostMapping(value = "/token", consumes = "application/x-www-form-urlencoded")
    public Map<String, Object> token(@RequestParam String grant_type,
                                     @RequestParam String client_id,
                                     @RequestParam(required = false) String client_secret,
                                     @RequestParam(required = false) String code,
                                     @RequestParam(required = false) String redirect_uri,
                                     @RequestParam(required = false) String code_verifier,
                                     @RequestParam(required = false) String refresh_token,
                                     @RequestParam(required = false) String username,
                                     @RequestParam(required = false) String password,
                                     @RequestParam(required = false) String scope) {
        return oauth.token(grant_type, client_id, client_secret,
                code, redirect_uri, code_verifier, refresh_token,
                username, password, scope);
    }

    /** RFC 7662 token introspection. */
    @PostMapping(value = "/introspect", consumes = "application/x-www-form-urlencoded")
    public Map<String, Object> introspect(@RequestParam String token,
                                          @RequestParam(required = false) String client_id,
                                          @RequestParam(required = false) String client_secret) {
        return oauth.introspect(token, client_id, client_secret);
    }

    /** RFC 7009 token revocation. */
    @PostMapping(value = "/revoke", consumes = "application/x-www-form-urlencoded")
    public Map<String, Object> revoke(@RequestParam String token,
                                      @RequestParam(required = false) String client_id,
                                      @RequestParam(required = false) String client_secret) {
        oauth.revoke(token, client_id, client_secret);
        Map<String, Object> m = new HashMap<>();
        m.put("status", "revoked");
        return m;
    }

    @GetMapping("/userinfo")
    public ApiResult<Map<String, Object>> userInfo(@AuthenticationPrincipal IamPrincipal p) {
        if (p == null || p.getUserId() == null) throw new AuthException("UNAUTHORIZED", "未登录");
        return ApiResult.ok(oauth.userInfo(p.getUserId()));
    }

    /** OIDC-style discovery document. */
    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery(HttpServletRequest req) {
        String base = ServletUriComponentsBuilder.fromRequest(req)
                .replacePath(req.getServletPath())
                .replaceQuery(null)
                .build()
                .toUriString();
        String issuer = base + "/oauth";

        Map<String, Object> m = new HashMap<>();
        m.put("issuer", issuer);
        m.put("authorization_endpoint", issuer + "/authorize");
        m.put("token_endpoint", issuer + "/token");
        m.put("userinfo_endpoint", issuer + "/userinfo");
        m.put("jwks_uri", issuer + "/jwks");
        m.put("introspection_endpoint", issuer + "/introspect");
        m.put("revocation_endpoint", issuer + "/revoke");
        m.put("response_types_supported", new String[]{"code"});
        m.put("grant_types_supported",
                new String[]{"authorization_code","refresh_token","password","client_credentials"});
        m.put("subject_types_supported", new String[]{"public"});
        m.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        m.put("code_challenge_methods_supported", new String[]{"S256"});
        m.put("scopes_supported", new String[]{"openid","profile","email","phone"});
        return m;
    }

    /** RFC 7517 JWKS — returns the RSA public key(s) for RS256 verification by resource servers. */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(jwt.jwk()));
    }
}

