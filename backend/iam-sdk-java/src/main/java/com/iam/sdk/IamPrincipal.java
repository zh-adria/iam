package com.iam.sdk;

import io.jsonwebtoken.Claims;

import java.util.List;

public class IamPrincipal {
    private final Long userId;
    private final String username;
    private final String tenant;
    private final String clientId;
    private final List<String> roles;
    private final List<String> permissions;
    private final String scope;

    public IamPrincipal(Long userId, String username, String tenant, String clientId,
                        List<String> roles, List<String> permissions, String scope) {
        this.userId = userId;
        this.username = username;
        this.tenant = tenant;
        this.clientId = clientId;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.scope = scope;
    }

    @SuppressWarnings("unchecked")
    public static IamPrincipal fromClaims(Claims claims) {
        Object uid = claims.get("uid");
        Long userId = uid instanceof Number ? ((Number) uid).longValue() : null;
        return new IamPrincipal(
                userId,
                claims.getSubject(),
                claims.get("tenant", String.class),
                claims.get("cid", String.class),
                (List<String>) claims.get("roles", List.class),
                (List<String>) claims.get("perms", List.class),
                claims.get("scope", String.class));
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getTenant() {
        return tenant;
    }

    public String getClientId() {
        return clientId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getScope() {
        return scope;
    }
}
