package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "iam_audit_log",
       indexes = {
           @Index(name = "idx_audit_user", columnList = "userId"),
           @Index(name = "idx_audit_time", columnList = "occurredAt")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Column(nullable = false, length = 32)
    private String tenantCode;
    @Column(nullable = false, length = 32)
    private String action; // LOGIN, LOGOUT, TOKEN_ISSUE, PERM_CHANGE...
    @Column(nullable = false, length = 16)
    private String result; // SUCCESS, FAIL
    private String principal;
    private String ip;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
    private String hashChainPrev; // tamper-proof: hash of previous row

    @PrePersist void pre() { occurredAt = Instant.now(); }
}
