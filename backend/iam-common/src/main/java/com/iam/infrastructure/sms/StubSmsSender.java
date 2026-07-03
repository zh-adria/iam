package com.iam.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default SMS sender — logs the code to console. Used during development
 * or when no SMS provider is configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iam.sms.provider", havingValue = "stub", matchIfMissing = true)
public class StubSmsSender implements SmsSender {

    @Override
    public String send(String phone, String code) {
        log.info("[SMS-STUB] send to {}: code={}", phone, code);
        return "STUB-" + System.identityHashCode(phone);
    }

    @Override
    public String providerName() { return "stub"; }
}
