package com.iam.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamAuthorizerTest {
    @Test
    void checksRolesAndPermissions() {
        IamPrincipal principal = new IamPrincipal(
                1L,
                "admin",
                "default",
                "demo-client",
                List.of("ROLE_ADMIN"),
                List.of("iam:role:create"),
                "openid iam:role:create");

        assertTrue(IamAuthorizer.hasRole(principal, "ROLE_ADMIN"));
        assertFalse(IamAuthorizer.hasRole(principal, "ROLE_USER"));
        assertTrue(IamAuthorizer.hasPermission(principal, "iam:role:create"));
        assertFalse(IamAuthorizer.hasPermission(principal, "iam:user:delete"));
        assertDoesNotThrow(() -> IamAuthorizer.requirePermission(principal, "iam:role:create"));
        assertThrows(IamSdkException.class, () -> IamAuthorizer.requirePermission(principal, "iam:user:delete"));
    }
}
