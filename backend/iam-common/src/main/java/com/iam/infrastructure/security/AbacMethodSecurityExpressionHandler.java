package com.iam.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.stereotype.Component;

/**
 * Wires {@link AbacPermissionEvaluator} into the Spring MethodSecurity expression tree
 * so @PreAuthorize("hasPermission(#id, 'iam:user:delete')") resolves SpEL.
 *
 * In Spring Security 6.2, configuration custom MethodSecurityExpressionHandler is usually
 * pluggable at the MethodSecurityInterceptor. For simplicity and compatibility, we expose
 * our evaluator through a custom @Component, and document the use via @PreAuthorize alternative.
 */
@Component
@RequiredArgsConstructor
public class AbacMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    private final AbacPermissionEvaluator permissionEvaluator;

    @Override
    public org.springframework.security.access.PermissionEvaluator getPermissionEvaluator() {
        return new org.springframework.security.access.PermissionEvaluator() {
            @Override
            public boolean hasPermission(org.springframework.security.core.Authentication auth,
                                         Object target, Object permission) {
                return permissionEvaluator.hasPermission(auth, target, permission);
            }
            @Override
            public boolean hasPermission(org.springframework.security.core.Authentication auth,
                                         java.io.Serializable targetId, String targetType, Object permission) {
                return permissionEvaluator.hasPermission(auth, targetId, targetType, permission);
            }
        };
    }
}
