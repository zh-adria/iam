package com.iam.infrastructure.tenant;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate multi-tenancy (SCHEMA strategy) using our tenant-aware connection provider.
 * Enabled only when iam.tenant.multi-tenant-enabled=true (default false for backwards compatibility).
 */
@Configuration
@ConditionalOnProperty(name = "iam.tenant.multi-tenant-enabled", havingValue = "true")
@RequiredArgsConstructor
public class MultiTenancyConfig {

    private final TenantAwareConnectionProvider connectionProvider;
    private final CurrentTenantIdentifierResolverImpl tenantIdentifierResolver;

    @Bean
    public HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
