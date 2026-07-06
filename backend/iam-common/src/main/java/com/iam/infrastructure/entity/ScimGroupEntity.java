package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import java.util.List;

/**
 * SCIM 2.0 Group — members stored as JSON array of {"value":"userId","display":"username"}.
 */
@Entity
@Table(name = "auth_scim_group",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "external_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScimGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External ID from provisioning system (Okta group ID, etc.) */
    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    /** JSON array: [{"value":"123","display":"alice"},...] */
    @Lob
    @Column(name = "members")
    private String members;

    @Column(name = "tenant_code", length = 32)
    private String tenantCode;
}
