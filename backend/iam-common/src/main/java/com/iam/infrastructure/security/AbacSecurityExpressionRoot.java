package com.iam.infrastructure.security;

import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * Custom expression root that exposes our AbacPermissionEvaluator via hasPermission().
 */
public class AbacSecurityExpressionRoot extends SecurityExpressionRoot
        implements MethodSecurityExpressionOperations {

    private AbacPermissionEvaluator permissionEvaluator;
    private Object filterObject;
    private Object returnObject;
    private Object target;

    public AbacSecurityExpressionRoot(Authentication a) {
        super(a);
    }

    public void setPermissionEvaluator(AbacPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * hasPermission(target, permission) — delegates to AbacPermissionEvaluator which resolves
     * SpEL from iam_permission.spel_expression.
     */
    public boolean hasPermission(Object target, Object permission) {
        return permissionEvaluator.hasPermission(getAuthentication(), target, permission);
    }

    public boolean hasPermission(java.io.Serializable targetId, String targetType, Object permission) {
        return permissionEvaluator.hasPermission(getAuthentication(), targetId, targetType, permission);
    }

    @Override
    public void setFilterObject(Object filterObject) { this.filterObject = filterObject; }
    @Override
    public Object getFilterObject() { return filterObject; }
    @Override
    public void setReturnObject(Object returnObject) { this.returnObject = returnObject; }
    @Override
    public Object getReturnObject() { return returnObject; }
    @Override
    public Object getThis() { return target; }
}
