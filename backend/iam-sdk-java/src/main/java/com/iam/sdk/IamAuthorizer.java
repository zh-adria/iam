package com.iam.sdk;

public final class IamAuthorizer {
    private IamAuthorizer() {
    }

    public static boolean hasRole(IamPrincipal principal, String role) {
        return principal != null && principal.getRoles().contains(role);
    }

    public static boolean hasPermission(IamPrincipal principal, String permission) {
        return principal != null && principal.getPermissions().contains(permission);
    }

    public static void requireRole(IamPrincipal principal, String role) {
        if (!hasRole(principal, role)) {
            throw new IamSdkException("Missing required role: " + role);
        }
    }

    public static void requirePermission(IamPrincipal principal, String permission) {
        if (!hasPermission(principal, permission)) {
            throw new IamSdkException("Missing required permission: " + permission);
        }
    }
}
