package com.iam.start.config;

import com.iam.infrastructure.entity.SamlIdpRegistrationEntity;
import com.iam.infrastructure.repository.SamlIdpRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps registrations in a ConcurrentHashMap and supports runtime refresh from DB.
 */
public class RefreshableRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {

    private final ConcurrentHashMap<String, RelyingPartyRegistration> map = new ConcurrentHashMap<>();

    public void setRegistrations(List<RelyingPartyRegistration> regs) {
        map.clear();
        if (regs != null) {
            for (RelyingPartyRegistration r : regs) {
                map.put(r.getRegistrationId(), r);
            }
        }
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        return map.get(registrationId);
    }
}
