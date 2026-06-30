package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.infrastructure.security.IamPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub protocol endpoints — WebAuthn / Kerberos / SCIM 2.0.
 * ponytail: these return 501 or minimal stubs. Wire real libs (spring-security-webauthn, kerb/Spnego, SCIM SDK) for prod.
 * SAML IdP metadata URL auto-load is in SamlConfig.
 */
@RestController
@RequiredArgsConstructor
public class StubProtocolController {

    @Value("${iam.webauthn.enabled:false}") private boolean webauthnEnabled;
    @Value("${iam.webauthn.rp-id:localhost}") private String rpId;
    @Value("${iam.webauthn.rp-name:IAM-Platform}") private String rpName;
    @Value("${iam.webauthn.origin:http://localhost:5173}") private String origin;
    @Value("${iam.scim.enabled:false}") private boolean scimEnabled;

    // ---------- WebAuthn / FIDO2 ----------
    @PostMapping("/api/auth/webauthn/register/begin")
    public ApiResult<Object> webauthnRegisterBegin(@AuthenticationPrincipal IamPrincipal p,
                                                    HttpServletResponse res) throws IOException {
        if (!webauthnEnabled) {
            res.sendError(501, "WebAuthn 未启用 — 配置 iam.webauthn.enabled=true 并接入 spring-security-webauthn");
            return null;
        }
        // ponytail: real impl returns PublicKeyCredentialCreationOptions from WebAuthnManager
        Map<String, Object> o = new HashMap<>();
        o.put("rp", Map.of("id", rpId, "name", rpName));
        o.put("user", Map.of("id", p == null ? "anon" : String.valueOf(p.getUserId()), "name", p == null ? "anon" : p.getUsername()));
        o.put("challenge", "STUB_CHALLENGE_REPLACE_WITH_REAL_RANDOM");
        return ApiResult.ok(o);
    }

    @PostMapping("/api/auth/webauthn/register/finish")
    public ApiResult<Void> webauthnRegisterFinish(HttpServletResponse res) throws IOException {
        if (!webauthnEnabled) { res.sendError(501, "WebAuthn 未启用"); return null; }
        res.sendError(501, "WebAuthn finish 需接入 WebAuthnManager.verifyRegistrationResponse");
        return null;
    }

    @PostMapping("/api/auth/webauthn/auth/begin")
    public ApiResult<Object> webauthnAuthBegin(HttpServletResponse res) throws IOException {
        if (!webauthnEnabled) { res.sendError(501, "WebAuthn 未启用"); return null; }
        Map<String, Object> o = new HashMap<>();
        o.put("challenge", "STUB_AUTH_CHALLENGE");
        o.put("rpId", rpId);
        return ApiResult.ok(o);
    }

    @PostMapping("/api/auth/webauthn/auth/finish")
    public ApiResult<Void> webauthnAuthFinish(HttpServletResponse res) throws IOException {
        if (!webauthnEnabled) { res.sendError(501, "WebAuthn 未启用"); return null; }
        res.sendError(501, "WebAuthn auth finish 需接入 WebAuthnManager.verifyAssertionResponse");
        return null;
    }

    // ---------- SCIM 2.0 (RFC 7643/7644) ----------
    @GetMapping("/scim/v2/Users")
    public Map<String, Object> scimListUsers(HttpServletResponse res) throws IOException {
        if (!scimEnabled) { res.sendError(501, "SCIM 未启用 — 配置 iam.scim.enabled=true"); return null; }
        // ponytail: returns empty list. Implement per-resource GET/POST/PATCH/DELETE when federating with IdP.
        Map<String, Object> r = new HashMap<>();
        r.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        r.put("totalResults", 0);
        r.put("Resources", List.of());
        return r;
    }

    @GetMapping("/scim/v2/ServiceProviderConfigs")
    public Map<String, Object> scimServiceProviderConfig() {
        Map<String, Object> r = new HashMap<>();
        r.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        r.put("patch", Map.of("supported", scimEnabled));
        r.put("documentationUri", "https://datatracker.ietf.org/doc/rfc7644/");
        return r;
    }
}
