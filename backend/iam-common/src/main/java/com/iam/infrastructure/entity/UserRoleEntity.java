package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "auth_user_role",
       uniqueConstraints = @UniqueConstraint(columnNames = {"userId","roleCode"}),
       indexes = @Index(name = "idx_ur_user", columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserRoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 64)
    private String roleCode;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Instant grantedAt = Instant.now();
}
