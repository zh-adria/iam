package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.domain.AuthException;
import com.iam.infrastructure.security.AbacPermissionEvaluator;
import com.iam.infrastructure.security.IamPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/authz")
@RequiredArgsConstructor
public class AuthzController {
    private final AbacPermissionEvaluator permissionEvaluator;

    @PostMapping("/check")
    public ApiResult<Map<String, Object>> check(@RequestBody Map<String, Object> body,
                                                Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IamPrincipal)) {
            throw new AuthException("UNAUTHORIZED", "Bearer token required");
        }
        Object permission = body.get("permission");
        if (permission == null || permission.toString().isBlank()) {
            throw new AuthException("BAD_REQUEST", "permission is required");
        }

        Object target = body.get("target");
        boolean allowed = permissionEvaluator.hasPermission(authentication, target, permission);
        IamPrincipal principal = (IamPrincipal) authentication.getPrincipal();

        Map<String, Object> result = new HashMap<>();
        result.put("allowed", allowed);
        result.put("permission", permission);
        result.put("uid", principal.getUserId());
        result.put("sub", principal.getUsername());
        result.put("tenant", principal.getTenantCode());
        return ApiResult.ok(result);
    }
}
