package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.GenerationType;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "auth_oauth2_client")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuth2ClientEntity {
    @Id @Column(length = 64)
    private String clientId;
    @Column(nullable = false)
    private String clientSecretHash;
    @Column(nullable = false)
    private String grantTypes; // comma: authorization_code,refresh_token,password,client_credentials
    private String redirectUris;
    private String scopes;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Integer accessTokenTtlMinutes = 30;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Integer refreshTokenTtlDays = 30;
    @Column(nullable = false)
    @lombok.Builder.Default
    private Boolean autoApprove = false;
    /** OIDC per-client extra claims for ID Token (JSON). */
    @Lob
    @Column(name = "id_token_claims")
    private String idTokenClaims;
    @Column(nullable = false, updatable = false)
    @lombok.Builder.Default
    private Instant createdAt = Instant.now();
}
