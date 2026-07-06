package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.LdapGroupRoleMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LdapGroupRoleMappingRepository extends JpaRepository<LdapGroupRoleMappingEntity, Long> {
    List<LdapGroupRoleMappingEntity> findByTenantCode(String tenantCode);
    List<LdapGroupRoleMappingEntity> findByTenantCodeAndLdapGroupDn(String tenantCode, String ldapGroupDn);
    void deleteByTenantCodeAndLdapGroupDn(String tenantCode, String ldapGroupDn);
}
