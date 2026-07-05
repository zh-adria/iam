package com.iam.infrastructure.entity;

import lombok.*;
import javax.persistence.*;

/**
 * Per-tenant SAML IdP registration. Replaces the application.yml static config with
 * dynamic DB-driven registration. Multiple IdPs per tenant supported.
 */
@Entity
@Table(name = "auth_saml_idp_registration",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code","registration_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SamlIdpRegistrationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", nullable = false, length = 32)
    private String tenantCode;

    /** Unique per-tenant registration id (used in /saml2/authenticate/{registrationId} etc.) */
    @Column(name = "registration_id", nullable = false, length = 64)
    private String registrationId;

    /** Remote IdP entity ID */
    @Column(name = "idp_entity_id", nullable = false)
    private String idpEntityId;

    /** IdP SSO URL (for manual /metadata-url-less setup) */
    @Column(name = "idp_sso_url")
    private String idpSsoUrl;

    /** URL to fetch IdP XML metadata from (optional — preferred over manual fields) */
    @Column(name = "idp_metadata_url")
    private String idpMetadataUrl;

    /** Raw IdP XML metadata (inline, alternative to url). */
    @Lob
    @Column(name = "idp_metadata_xml")
    private String idpMetadataXml;

    /** SP entity id to use for this registration (defaults to tenant_code) */
    @Column(name = "sp_entity_id", length = 255)
    private String spEntityId;

    /** Assertion consumer service location template. {registrationId} is replaced. */
    @Column(name = "acs_template", length = 255)
    private String acsTemplate;

    /** Whether this IdP is enabled for SSO */
    @Column(name = "enabled", nullable = false)
    @lombok.Builder.Default
    private Boolean enabled = true;
}
