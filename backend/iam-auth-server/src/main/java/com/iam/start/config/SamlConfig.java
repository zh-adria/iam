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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            // 没有任何 IdP 时 SAM2 Login 不要注册 placeholder — placeholder 常被 /
            // 触发导致 302 redirect loop（因为 registrationId="disabled" 的
            // assertionConsumerServiceLocation 无实际处理端点，会被无限 redirect）。
            // 不启用 saml2Login filter 完全禁用这个 feature。
            log.warn("No SAML IdP configured — SAML 2.0 SP disabled (no saml2Login filter)");
            return new InMemoryRelyingPartyRegistrationRepository(Collections.emptyList());
        }

        log.info("Loaded SAML IdP registrations: {}", regs.stream().map(RelyingPartyRegistration::getRegistrationId).collect(Collectors.joining(",")));
        return new InMemoryRelyingPartyRegistrationRepository(regs);
    }

    private RelyingPartyRegistration toRegistration(SamlIdpRegistrationEntity db) {
        String spId = db.getSpEntityId() != null ? db.getSpEntityId() : db.getTenantCode();
        String acs = db.getAcsTemplate() != null
                ? db.getAcsTemplate().replace("{registrationId}", db.getRegistrationId())
                : ymlAcsBase + "/" + db.getRegistrationId();

        if (db.getIdpMetadataUrl() != null && !db.getIdpMetadataUrl().isEmpty()) {
            try {
                return RelyingPartyRegistrations.fromMetadataLocation(db.getIdpMetadataUrl())
                        .registrationId(db.getRegistrationId())
                        .entityId(spId)
                        .assertionConsumerServiceLocation(acs)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to load IdP metadata from {} for reg {}: {}", db.getIdpMetadataUrl(), db.getRegistrationId(), e.getMessage());
            }
        }

        return RelyingPartyRegistration
                .withRegistrationId(db.getRegistrationId())
                .entityId(spId)
                .assertionConsumerServiceLocation(acs)
                .idpWebSsoUrl(db.getIdpSsoUrl())
                .remoteIdpEntityId(db.getIdpEntityId())
                .build();
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

            UserEntity user = socialRepo.findByProviderAndProviderUserId("saml", nameId)
                    .flatMap(b -> userRepo.findById(b.getUserId()))
                    .orElseGet(() -> provisionUser(nameId, email));

            String access = jwt.issueAccess(user.getId(), user.getUsername(), user.getTenantCode(),
                    List.of(), List.of(), "saml");
            audit.record(user.getId(), user.getTenantCode(), "SAML_LOGIN", "SUCCESS",
                    user.getUsername(), req.getRemoteAddr(), "nameId=" + nameId);
            res.sendRedirect(frontendRedirect + "?saml_token=" + access);
        }

        private UserEntity provisionUser(String nameId, String email) {
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
