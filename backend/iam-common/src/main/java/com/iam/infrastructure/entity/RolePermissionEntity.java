package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "iam_role_permission",
       uniqueConstraints = @UniqueConstraint(columnNames = {"roleCode","permCode"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RolePermissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String roleCode;
    @Column(nullable = false, length = 128)
    private String permCode;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Instant grantedAt = Instant.now();
}
