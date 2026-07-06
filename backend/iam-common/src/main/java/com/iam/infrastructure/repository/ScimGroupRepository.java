package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.ScimGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScimGroupRepository extends JpaRepository<ScimGroupEntity, Long> {
    List<ScimGroupEntity> findByTenantCode(String tenantCode);
    Optional<ScimGroupEntity> findByTenantCodeAndExternalId(String tenantCode, String externalId);
    List<ScimGroupEntity> findByTenantCodeAndDisplayNameContainingIgnoreCase(String tenantCode, String displayName);
}
