package com.iam.infrastructure.tenant;

import com.iam.infrastructure.entity.TenantEntity;
import com.iam.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hibernate MultiTenantConnectionProvider — routes DML to per-tenant MySQL schemas
 * when tenant.isolation_mode = 'SCHEMA_PER_TENANT'.
 *
 * SHARED (default): uses the default DataSource → public schema.
 * SCHEMA_PER_TENANT: same physical datasource, `USE tenant_xxx` statement to switch schema.
 *
 * Enabled only when iam.tenant.multi-tenant-enabled=true so dev/test stays backwards-compatible.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iam.tenant.multi-tenant-enabled", havingValue = "true")
@RequiredArgsConstructor
public class TenantAwareConnectionProvider extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl {

    private final TenantRepository tenantRepo;
    private final DataSource dataSource;

    /** Cache: tenant code → schema name. */
    private final Map<String, String> schemaCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warmCache() {
        tenantRepo.findAll().stream()
                .filter(t -> "SCHEMA_PER_TENANT".equals(t.getIsolationMode()))
                .forEach(t -> schemaCache.put(t.getCode(), resolveSchemaName(t)));
    }

    @Override
    protected DataSource selectAnyDataSource() {
        return dataSource;
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        String schema = schemaCache.computeIfAbsent(tenantIdentifier, tc ->
                tenantRepo.findByCode(tc)
                        .filter(t -> "SCHEMA_PER_TENANT".equals(t.getIsolationMode()))
                        .map(TenantAwareConnectionProvider.this::resolveSchemaName)
                        .orElse(null));
        if (schema == null) {
            return dataSource;
        }
        log.debug("Routing tenant {} to schema {}", tenantIdentifier, schema);
        return new SchemaRoutingDataSource(dataSource, schema);
    }

    private String resolveSchemaName(TenantEntity t) {
        return (t.getSchemaName() != null && !t.getSchemaName().isEmpty())
                ? t.getSchemaName()
                : "iam_tenant_" + t.getCode();
    }
}
