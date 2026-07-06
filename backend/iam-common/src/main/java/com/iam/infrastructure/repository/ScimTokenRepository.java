package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.ScimTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScimTokenRepository extends JpaRepository<ScimTokenEntity, Long> {
    List<ScimTokenEntity> findByEnabledTrue();
    Optional<ScimTokenEntity> findByTokenPrefix(String tokenPrefix);
    List<ScimTokenEntity> findByTenantCodeAndEnabledTrue(String tenantCode);
}
