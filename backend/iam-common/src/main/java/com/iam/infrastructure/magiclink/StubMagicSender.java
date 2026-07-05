package com.iam.infrastructure.magiclink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubMagicSender implements MagicSender {

    @Override
    public void send(String email, String link, int ttlMinutes) {
        log.info("[MAGIC-LINK-STUB] to {}: {} (ttl={}min)", email, link, ttlMinutes);
    }

    @Override
    public String providerName() { return "stub"; }
}
