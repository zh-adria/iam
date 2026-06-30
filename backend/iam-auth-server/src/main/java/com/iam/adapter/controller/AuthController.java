package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.app.dto.LoginCommand;
import com.iam.app.dto.TokenResponse;
import com.iam.app.service.AuthAppService;
import com.iam.infrastructure.security.IamPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAppService auth;
    private final com.iam.app.service.LdapAuthService ldapAuth;
    private final com.iam.app.service.SocialLoginService social;
    private final com.iam.app.service.SmsCodeService sms;
    private final com.iam.app.service.MagicLinkService magic;
    private final com.iam.app.service.CasAuthService cas;

    @PostMapping("/login")
    public ApiResult<TokenResponse> login(@RequestBody LoginCommand cmd, HttpServletRequest req) {
        cmd.setIp(clientIp(req));
        cmd.setUserAgent(req.getHeader("User-Agent"));
        if (cmd.getClientId() == null) cmd.setClientId("iam-self");
        if (cmd.getGrantType() == null) cmd.setGrantType("password");
        return ApiResult.ok(auth.login(cmd));
    }

    @PostMapping("/mfa/verify")
    public ApiResult<TokenResponse> verifyMfa(@RequestBody Map<String, String> body, HttpServletRequest req) {
        return ApiResult.ok(auth.verifyMfa(body.get("mfaToken"), body.get("code"), clientIp(req)));
    }

    @PostMapping("/refresh")
    public ApiResult<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        return ApiResult.ok(auth.refresh(body.get("refreshToken"), body.getOrDefault("clientId", "iam-self")));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader("Authorization") String authz, HttpServletRequest req) {
        auth.logout(authz.substring(7), clientIp(req));
        return ApiResult.ok(null, "已登出");
    }

    @PostMapping("/ldap")
    public ApiResult<com.iam.app.dto.TokenResponse> ldap(@RequestBody Map<String, String> body, HttpServletRequest req) {
        return ApiResult.ok(ldapAuth.authenticate(
                body.get("username"), body.get("password"),
                body.get("tenantCode"), clientIp(req)));
    }

    @GetMapping("/me")
    public ApiResult<IamPrincipal> me(Authentication authn) {
        return ApiResult.ok((IamPrincipal) authn.getPrincipal());
    }

    // ---------- social login ----------
    @GetMapping("/social/{provider}/authorize")
    public ApiResult<String> socialAuthorize(@PathVariable String provider) {
        return ApiResult.ok(social.authorizeUrl(provider));
    }

    @GetMapping("/social/{provider}/callback")
    public void socialCallback(@PathVariable String provider,
                               @RequestParam String code,
                               HttpServletRequest req,
                               HttpServletResponse res) throws IOException {
        // ponytail: redirect to frontend with token in query param — SPA picks it up.
        // Upgrade: HttpOnly cookie + SameSite to avoid token-in-URL leakage.
        TokenResponse t = social.callback(provider, code, clientIp(req));
        res.sendRedirect("http://localhost:5173/social-callback?token=" + t.getAccessToken());
    }

    // ---------- SMS code login ----------
    @PostMapping("/sms/send")
    public ApiResult<Void> smsSend(@RequestBody Map<String, String> body) {
        sms.send(body.get("phone"));
        return ApiResult.ok(null, "验证码已发送");
    }

    @PostMapping("/sms/login")
    public ApiResult<com.iam.app.dto.TokenResponse> smsLogin(@RequestBody Map<String, String> body,
                                                              HttpServletRequest req) {
        return ApiResult.ok(sms.loginOrProvision(body.get("phone"), body.get("code"), clientIp(req)));
    }

    // ---------- Magic Link ----------
    @PostMapping("/magic/send")
    public ApiResult<Void> magicSend(@RequestBody Map<String, String> body, HttpServletRequest req) {
        magic.send(body.get("email"), clientIp(req));
        return ApiResult.ok(null, "登录链接已发送");
    }

    @GetMapping("/magic/verify")
    public ApiResult<com.iam.app.dto.TokenResponse> magicVerify(@RequestParam String token, HttpServletRequest req) {
        return ApiResult.ok(magic.verify(token, clientIp(req)));
    }

    // ---------- CAS SSO ----------
    @GetMapping("/cas/authorize")
    public ApiResult<String> casAuthorize() {
        return ApiResult.ok(cas.authorizeUrl());
    }

    @GetMapping("/cas/callback")
    public void casCallback(@RequestParam String ticket, HttpServletRequest req, HttpServletResponse res) throws IOException {
        TokenResponse t = cas.callback(ticket, clientIp(req));
        res.sendRedirect("http://localhost:5173/social-callback?token=" + t.getAccessToken());
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
