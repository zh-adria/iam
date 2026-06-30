package com.iam.start.config;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.AuthAppService;
import com.iam.app.dto.LoginCommand;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * SAML 2.0 Service Provider config.
 *
 * ponytail: Single IdP wired from application.yml (iam.saml.idp.metadata-url + entity-id + sso-url).
 * For multi-IdP, populate RelyingPartyRegistrationRepository with multiple registrations keyed by registrationId.
 *
 * Flow: SP-initiated → GET /saml2/authenticate/{registrationId} → redirect to IdP →
 *       POST /login/saml2/sso/{registrationId} (ACS) → Saml2WebSsoAuthenticationFilter authenticates →
 *       SamlSuccessHandler issues JWT and redirects to frontend.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SamlConfig {

    @Value("${iam.saml.idp.metadata-url:}")
    private String idpMetadataUrl;
    @Value("${iam.saml.idp.entity-id:}")
    private String idpEntityId;
    @Value("${iam.saml.idp.sso-url:}")
    private String idpSsoUrl;
    @Value("${iam.saml.sp.entity-id:urn:iam:sp}")
    private String spEntityId;
    @Value("${iam.saml.sp.acs-base:http://localhost:8080/iam/login/saml2/sso}")
    private String spAcsBase;
    @Value("${iam.saml.sp.frontend-redirect:http://localhost:5173/dashboard}")
    private String frontendRedirect;

    private final UserRepository userRepo;
    private final SocialBindingRepository socialRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        RelyingPartyRegistration registration;
        if (idpMetadataUrl != null && !idpMetadataUrl.isEmpty()) {
            // Auto-load IdP metadata from URL (XML metadata feed — Azure AD / Shibboleth / Keycloak publish this).
            try {
                registration = RelyingPartyRegistrations.fromMetadataLocation(idpMetadataUrl)
                        .entityId(spEntityId)
                        .assertionConsumerServiceLocation(spAcsBase + "/default")
                        .registrationId("default")
                        .build();
                log.info("SAML IdP metadata loaded from {}", idpMetadataUrl);
            } catch (Exception e) {
                log.error("Failed to load SAML metadata from {}: {}", idpMetadataUrl, e.getMessage());
                throw new IllegalStateException("SAML metadata load failed", e);
            }
        } else if (idpEntityId != null && !idpEntityId.isEmpty()) {
            registration = RelyingPartyRegistration
                    .withRegistrationId("default")
                    .entityId(spEntityId)
                    .assertionConsumerServiceLocation(spAcsBase + "/default")
                    .idpWebSsoUrl(idpSsoUrl)
                    .remoteIdpEntityId(idpEntityId)
                    .build();
        } else {
            log.warn("SAML IdP not configured (iam.saml.idp.entity-id/metadata-url empty) — providing placeholder registration");
            registration = RelyingPartyRegistration
                    .withRegistrationId("default")
                    .entityId(spEntityId)
                    .assertionConsumerServiceLocation(spAcsBase + "/default")
                    .idpWebSsoUrl("http://localhost:0/placeholder")
                    .remoteIdpEntityId("urn:placeholder:idp")
                    .build();
        }
        return new InMemoryRelyingPartyRegistrationRepository(List.of(registration));
    }

    @Bean
    public AuthenticationSuccessHandler samlSuccessHandler() {
        return new SamlSuccessHandler(userRepo, socialRepo, jwt, audit, frontendRedirect);
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
            String email = firstAttr(p, "email");
            log.info("SAML login success nameId={} email={}", nameId, email);

            // resolve or provision user via social binding
            UserEntity user = socialRepo.findByProviderAndProviderUserId("saml", nameId)
                    .flatMap(b -> userRepo.findById(b.getUserId()))
                    .orElseGet(() -> provisionUser(nameId, email));

            String access = jwt.issueAccess(user.getId(), user.getUsername(), user.getTenantCode(),
                    List.of(), List.of(), "saml");
            audit.record(user.getId(), user.getTenantCode(), "SAML_LOGIN", "SUCCESS",
                    user.getUsername(), req.getRemoteAddr(), "nameId=" + nameId);
            // ponytail: passing token via query param — frontend should immediately exchange for cookie/refresh.
            // Upgrade: set HttpOnly cookie for SP-initiated flows.
            res.sendRedirect(frontendRedirect + "?saml_token=" + access);
        }

        private UserEntity provisionUser(String nameId, String email) {
            // ponytail: auto-provision on first SAML login. For closed user-base, disable and pre-bind via admin.
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
                    .providerUsername(email == null ? nameId : email)
                    .build());
            return u;
        }

        private static String firstAttr(Saml2AuthenticatedPrincipal p, String name) {
            List<String> v = p.getAttribute(name);
            return (v == null || v.isEmpty()) ? null : v.get(0);
        }
    }
}
