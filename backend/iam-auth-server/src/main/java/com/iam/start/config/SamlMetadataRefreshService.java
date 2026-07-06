package com.iam.start.config;

import com.iam.infrastructure.entity.SamlIdpRegistrationEntity;
import com.iam.infrastructure.repository.SamlIdpRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Background poller that refreshes IdP metadata XML from metadata URLs.
 */
@Slf4j
@RequiredArgsConstructor
public class SamlMetadataRefreshService {

    private final SamlIdpRegistrationRepository samlRepo;
    private final RelyingPartyRegistrationRepository regRepo;
    private final SamlConfig samlConfig;

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelayString = "${iam.saml.metadata-refresh-interval:3600000}")
    @Transactional
    public void refresh() {
        List<SamlIdpRegistrationEntity> all = samlRepo.findAll();
        List<RelyingPartyRegistration> updated = new ArrayList<>();
        boolean changed = false;

        for (SamlIdpRegistrationEntity e : all) {
            if (e.getIdpMetadataUrl() == null || e.getIdpMetadataUrl().isEmpty()) {
                updated.add(samlConfig.toRegistration(e));
                continue;
            }
            Instant last = e.getMetadataLastRefreshedAt();
            int interval = e.getMetadataRefreshIntervalHours() != null ? e.getMetadataRefreshIntervalHours() : 6;
            if (last != null && last.isAfter(Instant.now().minusSeconds(interval * 3600L))) {
                updated.add(samlConfig.toRegistration(e));
                continue;
            }
            try {
                String xml = new String(new java.net.URI(e.getIdpMetadataUrl()).toURL().openStream()
                        .readAllBytes());
                e.setIdpMetadataXml(xml);
                e.setMetadataLastRefreshedAt(Instant.now());
                samlRepo.save(e);
                changed = true;
                log.info("Refreshed SAML metadata for reg={}", e.getRegistrationId());
            } catch (Exception ex) {
                log.warn("Metadata refresh failed for reg={}: {}", e.getRegistrationId(), ex.getMessage());
            }
            updated.add(samlConfig.toRegistration(e));
        }

        if (changed) {
            ((RefreshableRelyingPartyRegistrationRepository) regRepo).setRegistrations(updated);
        }
    }
}
