package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {
    List<RolePermissionEntity> findByRoleCode(String roleCode);
    @Query("select distinct p.code from RolePermissionEntity rp join PermissionEntity p on rp.permCode = p.code where rp.roleCode in :roleCodes")
    List<String> findPermCodesByRoleCodes(@Param("roleCodes") List<String> roleCodes);
}
