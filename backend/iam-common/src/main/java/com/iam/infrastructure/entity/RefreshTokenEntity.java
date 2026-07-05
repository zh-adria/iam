package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "auth_refresh_token",
       indexes = {
           @Index(name = "idx_rt_token", columnList = "token"),
           @Index(name = "idx_rt_user", columnList = "userId")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshTokenEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String token;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 32)
    private String tenantCode;
    @Column(nullable = false)
    private String clientId;
    @Column(nullable = false)
    private String scopes;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant issuedAt = Instant.now();
    private Instant revokedAt;
}
