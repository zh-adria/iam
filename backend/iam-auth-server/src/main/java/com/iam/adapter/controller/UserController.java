package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.app.service.UserAppService;
import com.iam.infrastructure.security.IamPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User self-service endpoints (authserver profile).
 * Admin-facing user/role/permission CRUD lives in AdminUserController under /admin/api.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAppService users;

    @PostMapping("/register")
    public ApiResult<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ApiResult.ok(users.register(
                body.get("username"), body.get("password"),
                body.get("email"), body.get("phone"), body.get("tenantCode")));
    }

    @GetMapping("/me")
    public ApiResult<Map<String, Object>> profile(@AuthenticationPrincipal IamPrincipal p) {
        return ApiResult.ok(users.profile(p.getUserId()));
    }

    @PostMapping("/mfa/setup")
    public ApiResult<Map<String, Object>> setupMfa(@AuthenticationPrincipal IamPrincipal p) {
        return ApiResult.ok(users.setupMfa(p.getUserId()));
    }

    @PostMapping("/mfa/confirm")
    public ApiResult<Void> confirmMfa(@AuthenticationPrincipal IamPrincipal p, @RequestBody Map<String, String> body) {
        users.confirmMfa(p.getUserId(), body.get("code"));
        return ApiResult.ok(null, "MFA 已启用");
    }

    @PostMapping("/mfa/disable")
    public ApiResult<Void> disableMfa(@AuthenticationPrincipal IamPrincipal p) {
        users.disableMfa(p.getUserId());
        return ApiResult.ok(null, "MFA 已关闭");
    }
}
