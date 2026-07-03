package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.SamlIdpRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SamlIdpRegistrationRepository extends JpaRepository<SamlIdpRegistrationEntity, Long> {
    Optional<SamlIdpRegistrationEntity> findByTenantCodeAndRegistrationId(String tenantCode, String registrationId);
    List<SamlIdpRegistrationEntity> findByTenantCode(String tenantCode);
    List<SamlIdpRegistrationEntity> findByEnabledTrue();
}
