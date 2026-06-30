package com.iam.infrastructure.security;

import java.util.List;

public class IamPrincipal {
    private final Long userId;
    private final String username;
    private final String tenantCode;
    private final List<String> roles;

    public IamPrincipal(Long userId, String username, String tenantCode, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.tenantCode = tenantCode;
        this.roles = roles;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getTenantCode() { return tenantCode; }
    public List<String> getRoles() { return roles; }
}
