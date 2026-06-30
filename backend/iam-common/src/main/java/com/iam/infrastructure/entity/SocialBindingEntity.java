package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;

@Entity
@Table(name = "iam_social_binding",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider","providerUserId"}),
       indexes = @Index(name = "idx_sb_user", columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SocialBindingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 24)
    private String provider; // wechat, alipay, qq, dingtalk, workwx, google
    @Column(nullable = false)
    private String providerUserId;
    @Column(nullable = false)
    private String providerUsername;
    @Column(nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant boundAt = Instant.now();
}
