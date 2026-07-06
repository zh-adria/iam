package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import java.time.Instant;

/**
 * Maps LDAP group DN to IAM role code for automatic role assignment on LDAP login.
 */
@Entity
@Table(name = "admin_ldap_group_role_mapping",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "ldap_group_dn"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LdapGroupRoleMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", nullable = false, length = 32)
    private String tenantCode;

    /** LDAP group DN, e.g. "CN=Engineering,OU=Groups,DC=corp,DC=local" */
    @Column(name = "ldap_group_dn", nullable = false, length = 255)
    private String ldapGroupDn;

    /** IAM role code to assign */
    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
