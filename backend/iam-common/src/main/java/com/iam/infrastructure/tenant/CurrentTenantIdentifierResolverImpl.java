package com.iam.infrastructure.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the current tenant identifier from the thread-local set by JwtAuthFilter.
 * Returns "DEFAULT" for shared-schema tenants (backwards compatible).
 */
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver {

    public static final String DEFAULT_TENANT = "default";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String t = CurrentTenantHolder.get();
        return t == null ? DEFAULT_TENANT : t;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
