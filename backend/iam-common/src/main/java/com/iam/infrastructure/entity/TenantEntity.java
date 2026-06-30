package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "iam_tenant",
       uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenantEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 32)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 16)
    private String isolationMode; // SHARED, SCHEMA_PER_TENANT
    private String schemaName;
    private String ldapUrl;
    private String ldapBase;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Boolean enabled = true;
    @Column(nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
