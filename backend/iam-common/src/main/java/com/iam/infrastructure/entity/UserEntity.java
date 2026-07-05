package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "auth_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    private String email;
    private String phone;
    @Column(nullable = false, length = 32)
    private String tenantCode;
    @Column(nullable = false)
    private Integer status; // 1 active, 0 disabled, 2 locked
    private String mfaSecret;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Boolean mfaEnabled = false;
    @lombok.Builder.Default
    private Integer failCount = 0;
    private Instant lockedUntil;
    @Column(nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    @PrePersist void preInsert() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
