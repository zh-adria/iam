package com.iam.start.config;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.AuthAppService;
import com.iam.app.dto.LoginCommand;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.SamlIdpRegistrationEntity;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.SamlIdpRegistrationRepository;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SAML 2.0 Service Provider config — JPA-backed IdP registration.
 *
 * Registrations are loaded from iam_saml_idp_registration (all enabled rows).
 * Falls back to application.yml (iam.saml.idp.*) for backwards-compat when the table is empty.
 *
 * Flow: SP-initiated → GET /saml2/authenticate/{registrationId} → redirect to IdP →
 *       POST /login/saml2/sso/{registrationId} (ACS) → JWT issued → redirect to frontend.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iam.saml", name = "enabled", havingValue = "true", matchIfMissing = false)
@org.springframework.scheduling.annotation.EnableScheduling
public class SamlConfig {

    @Value("${iam.saml.idp.metadata-url:}")
    private String ymlIdpMetadataUrl;
    @Value("${iam.saml.idp.entity-id:}")
    private String ymlIdpEntityId;
    @Value("${iam.saml.idp.sso-url:}")
    private String ymlIdpSsoUrl;
    @Value("${iam.saml.sp.entity-id:urn:iam:sp}")
    private String ymlSpEntityId;
    @Value("${iam.saml.sp.acs-base:http://localhost:8080/iam/login/saml2/sso}")
    private String ymlAcsBase;
    @Value("${iam.saml.sp.frontend-redirect:http://localhost:5173/dashboard}")
    private String frontendRedirect;

    private final SamlIdpRegistrationRepository samlRepo;
    private final UserRepository userRepo;
    private final SocialBindingRepository socialRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        List<RelyingPartyRegistration> regs = buildRegistrations();
        RefreshableRelyingPartyRegistrationRepository repo = new RefreshableRelyingPartyRegistrationRepository();
        repo.setRegistrations(regs.isEmpty() ? null : regs);
        return repo;
    }

    private List<RelyingPartyRegistration> buildRegistrations() {
        List<RelyingPartyRegistration> regs = new ArrayList<>();

        // 1) Load from DB
        List<SamlIdpRegistrationEntity> dbRegs = samlRepo.findByEnabledTrue();
        for (SamlIdpRegistrationEntity db : dbRegs) {
            regs.add(toRegistration(db));
        }

        // 2) Fallback to application.yml (backwards compat with single-IdP config)
        if (regs.isEmpty() && ((ymlIdpMetadataUrl != null && !ymlIdpMetadataUrl.isEmpty())
                || (ymlIdpEntityId != null && !ymlIdpEntityId.isEmpty()))) {
            try {
                RelyingPartyRegistration r;
                if (ymlIdpMetadataUrl != null && !ymlIdpMetadataUrl.isEmpty()) {
                    r = RelyingPartyRegistrations.fromMetadataLocation(ymlIdpMetadataUrl)
                            .entityId(ymlSpEntityId)
                            .assertionConsumerServiceLocation(ymlAcsBase + "/default")
                            .registrationId("default")
                            .build();
                } else {
                    r = RelyingPartyRegistration
                            .withRegistrationId("default")
                            .entityId(ymlSpEntityId)
                            .assertionConsumerServiceLocation(ymlAcsBase + "/default")
                            .idpWebSsoUrl(ymlIdpSsoUrl)
                            .remoteIdpEntityId(ymlIdpEntityId)
                            .build();
                }
                regs.add(r);
            } catch (Exception e) {
                log.warn("Failed to load SAML IdP from application.yml: {}", e.getMessage());
            }
        }

        if (regs.isEmpty()) {
            log.warn("No SAML IdP configured — SAML 2.0 SP disabled");
        }

        log.info("Loaded SAML IdP registrations: {}", regs.stream().map(RelyingPartyRegistration::getRegistrationId).collect(Collectors.joining(",")));
        return regs;
    }

    RelyingPartyRegistration toRegistration(SamlIdpRegistrationEntity db) {
        return toRegistrationStatic(db, ymlAcsBase);
    }

    static RelyingPartyRegistration toRegistrationStatic(SamlIdpRegistrationEntity db, String ymlAcsBase) {
        String spId = db.getSpEntityId() != null ? db.getSpEntityId() : db.getTenantCode();
        String acs = db.getAcsTemplate() != null
                ? db.getAcsTemplate().replace("{registrationId}", db.getRegistrationId())
                : ymlAcsBase + "/" + db.getRegistrationId();

        if (db.getIdpMetadataUrl() != null && !db.getIdpMetadataUrl().isEmpty()) {
            try {
                RelyingPartyRegistration.Builder b = RelyingPartyRegistrations
                        .fromMetadataLocation(db.getIdpMetadataUrl())
                        .registrationId(db.getRegistrationId())
                        .entityId(spId)
                        .assertionConsumerServiceLocation(acs);
                if (db.getNameIdFormat() != null && !db.getNameIdFormat().isEmpty()) {
                    b.nameIdFormat(db.getNameIdFormat());
                }
                return b.build();
            } catch (Exception e) {
                log.warn("Failed to load IdP metadata from {} for reg {}: {}", db.getIdpMetadataUrl(), db.getRegistrationId(), e.getMessage());
            }
        }

        RelyingPartyRegistration.Builder b = RelyingPartyRegistration
                .withRegistrationId(db.getRegistrationId())
                .entityId(spId)
                .assertionConsumerServiceLocation(acs)
                .idpWebSsoUrl(db.getIdpSsoUrl())
                .remoteIdpEntityId(db.getIdpEntityId());
        if (db.getNameIdFormat() != null && !db.getNameIdFormat().isEmpty()) {
            b.nameIdFormat(db.getNameIdFormat());
        }
        return b.build();
    }

    @Bean
    public AuthenticationSuccessHandler samlSuccessHandler() {
        return new SamlSuccessHandler(userRepo, socialRepo, jwt, audit, frontendRedirect);
    }

    @Bean
    public SamlMetadataRefreshService samlMetadataRefreshService() {
        return new SamlMetadataRefreshService(samlRepo,
                relyingPartyRegistrationRepository(),
                this);
    }

    /** SP Metadata download endpoint: GET /saml2/metadata/{registrationId} */
    @GetMapping("/saml2/metadata/{registrationId}")
    public ResponseEntity<String> spMetadata(@PathVariable String registrationId) {
        RelyingPartyRegistrationRepository repo = relyingPartyRegistrationRepository();
        if (repo == null) {
            return ResponseEntity.status(404).body("SAML not enabled");
        }
        RelyingPartyRegistration reg = repo.findByRegistrationId(registrationId);
        if (reg == null) {
            return ResponseEntity.status(404).body("Registration not found: " + registrationId);
        }
        String xml = buildSpMetadata(reg);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_XML)
                .body(xml);
    }

    /** SP-initiated SAML Single Logout: redirects to IdP SLO endpoint. */
    @GetMapping("/saml2/logout/{registrationId}")
    public void samlLogout(@PathVariable String registrationId,
                           HttpServletRequest req, HttpServletResponse res) throws IOException {
        RelyingPartyRegistrationRepository repo = relyingPartyRegistrationRepository();
        if (repo == null) {
            res.sendRedirect(frontendRedirect);
            return;
        }
        RelyingPartyRegistration reg = repo.findByRegistrationId(registrationId);
        if (reg == null || reg.getSingleLogoutServiceLocation() == null) {
            res.sendRedirect(frontendRedirect);
            return;
        }
        // Revoke local session
        req.getSession().invalidate();
        String sloUrl = reg.getSingleLogoutServiceLocation() + "?SAMLRequest="
                + URLEncoder.encode(buildLogoutRequest(reg), StandardCharsets.UTF_8);
        res.sendRedirect(sloUrl);
    }

    private static String buildLogoutRequest(RelyingPartyRegistration reg) {
        String spEntityId = reg.getEntityId();
        String sloUrl = reg.getSingleLogoutServiceLocation();
        String sessionIndex = ""; // In production, track SessionIndex from SAML response
        String logoutRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<saml2p:LogoutRequest xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\""
                + " ID=\"" + java.util.UUID.randomUUID() + "\""
                + " Version=\"2.0\""
                + " IssueInstant=\"" + java.time.Instant.now().toString() + "\""
                + " Destination=\"" + sloUrl + "\""
                + " Issuer=\"" + spEntityId + "\""
                + ">"
                + "<saml2:Issuer xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">" + spEntityId + "</saml2:Issuer>"
                + "<saml2p:SessionIndex>" + sessionIndex + "</saml2p:SessionIndex>"
                + "</saml2p:LogoutRequest>";
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(logoutRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String buildSpMetadata(RelyingPartyRegistration reg) {
        String acs = reg.getAssertionConsumerServiceLocation();
        String now = java.time.Instant.now().toString();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\"\n" +
                "                  entityID=\"" + reg.getEntityId() + "\">\n" +
                "  <SPSSODescriptor AuthnRequestsSigned=\"true\" WantAssertionsSigned=\"true\"\n" +
                "                   protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n" +
                "    <AssertionConsumerService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"\n" +
                "                             Location=\"" + acs + "\" index=\"1\" isDefault=\"true\"/>\n" +
                "  </SPSSODescriptor>\n" +
                "</EntityDescriptor>";
    }

    @Slf4j
    @RequiredArgsConstructor
    static class SamlSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
        private final UserRepository userRepo;
        private final SocialBindingRepository socialRepo;
        private final JwtTokenService jwt;
        private final AuditLogService audit;
        private final String frontendRedirect;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                            org.springframework.security.core.Authentication auth)
                throws ServletException, IOException {
            Saml2AuthenticatedPrincipal p = (Saml2AuthenticatedPrincipal) auth.getPrincipal();
            String nameId = p.getName();

            // Resolve attributes via per-IdP attribute mapping
            Map<String, String> attr = resolveAttributes(p);

            String email = attr.getOrDefault("email", firstAttr(p, "email"));
            String displayName = attr.getOrDefault("displayName", email != null ? email : nameId);
            log.info("SAML login success nameId={} email={} displayName={}", nameId, email, displayName);

            UserEntity user = socialRepo.findByProviderAndProviderUserId("saml", nameId)
                    .flatMap(b -> userRepo.findById(b.getUserId()))
                    .orElseGet(() -> provisionUser(nameId, email, displayName));

            String access = jwt.issueAccess(user.getId(), user.getUsername(), user.getTenantCode(),
                    List.of(), List.of(), "saml");
            audit.record(user.getId(), user.getTenantCode(), "SAML_LOGIN", "SUCCESS",
                    user.getUsername(), req.getRemoteAddr(), "nameId=" + nameId);
            res.sendRedirect(frontendRedirect + "?saml_token=" + access);
        }

        private UserEntity provisionUser(String nameId, String email, String displayName) {
            UserEntity u = userRepo.save(UserEntity.builder()
                    .username("saml_" + nameId)
                    .passwordHash("DISABLED")  // SAML users can't password-login
                    .email(email)
                    .tenantCode("default")
                    .status(1).mfaEnabled(false).failCount(0)
                    .build());
            socialRepo.save(SocialBindingEntity.builder()
                    .userId(u.getId())
                    .provider("saml")
                    .providerUserId(nameId)
                    .providerUsername(displayName == null ? email : displayName)
                    .build());
            return u;
        }

        private static String firstAttr(Saml2AuthenticatedPrincipal p, String name) {
            List<String> v = p.getAttribute(name);
            return (v == null || v.isEmpty()) ? null : v.get(0);
        }

        private static Map<String, String> resolveAttributes(Saml2AuthenticatedPrincipal p) {
            Map<String, String> result = new LinkedHashMap<>();
            // default: try common SAML attributes directly
            result.put("email", firstAttr(p, "email"));
            result.put("displayName", firstAttr(p, "displayName") != null ? firstAttr(p, "displayName") : firstAttr(p, "cn"));
            result.put("firstName", firstAttr(p, "givenName"));
            return result;
        }
    }
}
