package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import java.time.Instant;

/**
 * SCIM 2.0 Group member — individual row for each member of a group.
 * Supports both internal (user_id) and external (member_value) members.
 */
@Entity
@Table(name = "auth_scim_group_member",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "member_value"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScimGroupMemberEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** "User" or "Group" */
    @Column(name = "member_type", nullable = false, length = 16)
    @lombok.Builder.Default
    private String memberType = "User";

    /** Internal user ID, null for external members */
    @Column(name = "user_id")
    private Long userId;

    /** SCIM $ref value or user ID string */
    @Column(name = "member_value", nullable = false, length = 128)
    private String memberValue;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "tenant_code", length = 32)
    private String tenantCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
