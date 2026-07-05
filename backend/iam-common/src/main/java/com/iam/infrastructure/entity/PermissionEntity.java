package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "auth_permission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String code;
    @Column(nullable = false, length = 16)
    private String type; // API / MENU / BUTTON / DATA
    private String name;
    private String resource;
    private String action;
    private String spelExpression;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
