package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_api_key",
       uniqueConstraints = {@UniqueConstraint(columnNames = "prefix"), @UniqueConstraint(columnNames = "key_hash")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiKeyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String prefix;              // e.g. "iam_live_abc12"

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;             // sha256(rawKey) — raw key only shown once at creation

    @Column(length = 64)
    private String name;                // human label

    @Column(length = 32)
    private String owner;               // user or service name

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String tenantCode = "default";

    @Column(length = 32)
    private String scope;               // comma-separated scopes: read,write,admin

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Instant expiresAt = Instant.ofEpochMilli(Long.MAX_VALUE); // no expiry if far future

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant lastUsedAt = Instant.ofEpochMilli(0L);

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
