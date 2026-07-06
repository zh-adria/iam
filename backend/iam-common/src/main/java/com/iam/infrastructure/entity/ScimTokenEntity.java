package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import java.time.Instant;

/**
 * SCIM 2.0 provisioner token — stored as bcrypt hash, prefix kept for identification.
 */
@Entity
@Table(name = "auth_scim_token",
       uniqueConstraints = @UniqueConstraint(columnNames = {"token_prefix"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScimTokenEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** bcrypt hash of the raw token */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    /** first 8 chars of raw token, for UI identification */
    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "tenant_code", length = 32)
    private String tenantCode;

    @Column(name = "scope", length = 255)
    private String scope;

    @Column(name = "enabled", nullable = false)
    @lombok.Builder.Default
    private Boolean enabled = true;

    /** 9999-12-31 means no expiry */
    @Column(name = "expires_at", nullable = false)
    @lombok.Builder.Default
    private Instant expiresAt = Instant.parse("9999-12-31T00:00:00Z");

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
