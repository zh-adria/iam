package com.iam.infrastructure.security;

import com.iam.infrastructure.entity.PermissionEntity;
import com.iam.infrastructure.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ABAC permission evaluator: resolves SpEL expressions stored in iam_permission.spel_expression
 * and evaluates them against the current authentication + target domain object.
 *
 * SpEL variables available in expressions:
 *   #auth    → the Authentication (principal is IamPrincipal)
 *   #uid     → current user id
 *   #tenant  → current tenant code
 *   #roles   → list of role codes
 *   #target  → the target domain object (entity id, dto, etc.)
 *   #action  → the permission code string passed to hasPermission()
 *
 * Example spel_expression: "#target == null or #target.tenantCode == #tenant"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbacPermissionEvaluator {

    private final PermissionRepository permissionRepo;
    private final org.springframework.expression.ExpressionParser parser =
            new org.springframework.expression.spel.standard.SpelExpressionParser();

    /** Cache of parsed SpEL expressions keyed by permission code. */
    private final Map<String, org.springframework.expression.Expression> expressionCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warmCache() {
        try {
            List<PermissionEntity> all = permissionRepo.findAll();
            for (PermissionEntity p : all) {
                if (p.getSpelExpression() != null && !p.getSpelExpression().isBlank()) {
                    expressionCache.put(p.getCode(), parser.parseExpression(p.getSpelExpression()));
                }
            }
            log.info("ABAC SpEL cache warmed: {} expressions from {} permissions", expressionCache.size(), all.size());
        } catch (Exception e) {
            log.warn("ABAC SpEL cache warm failed (will lazy-load): {}", e.getMessage());
        }
    }

    /**
     * Evaluate whether the authenticated user has the given permission against the target.
     *
     * @param auth       current authentication
     * @param target     target domain object (entity, id, dto, etc.)
     * @param permission permission code string (e.g. "iam:user:delete")
     * @return true if permission granted
     */
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(auth.getPrincipal() instanceof IamPrincipal)) return false;
        IamPrincipal p = (IamPrincipal) auth.getPrincipal();
        String permCode = permission.toString();

        // 1) user must hold this permission code (from JWT perms claim, surfaced as GrantedAuthority)
        boolean holds = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permCode::equals);
        if (!holds) return false;

        // 2) look up the permission entity
        PermissionEntity perm = permissionRepo.findByCode(permCode).orElse(null);
        if (perm == null) return true; // no entity → plain RBAC grant

        // 3) no SpEL → plain RBAC grant
        if (perm.getSpelExpression() == null || perm.getSpelExpression().isBlank()) return true;

        // 4) evaluate SpEL
        try {
            org.springframework.expression.Expression expr = expressionCache.computeIfAbsent(
                    perm.getCode(), k -> parser.parseExpression(perm.getSpelExpression()));
            var ctx = new org.springframework.expression.spel.support.StandardEvaluationContext();
            ctx.setVariable("auth", auth);
            ctx.setVariable("uid", p.getUserId());
            ctx.setVariable("tenant", p.getTenantCode());
            ctx.setVariable("roles", p.getRoles());
            ctx.setVariable("target", target);
            ctx.setVariable("action", permCode);
            Boolean result = expr.getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("SpEL eval failed for perm {}: {}", permCode, e.getMessage());
            return false;
        }
    }

    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        return hasPermission(auth, targetId, permission);
    }
}
