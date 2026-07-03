package com.iam.infrastructure.magiclink;

/**
 * Pluggable Magic Link sender. Implementations: stub (console) and SMTP.
 */
public interface MagicSender {

    /**
     * Send a magic login link to the user's email.
     *
     * @param email recipient email
     * @param link  full magic callback URL ?token=...
     * @param ttlMinutes link validity in minutes
     */
    void send(String email, String link, int ttlMinutes);

    /** Provider name for logging. */
    String providerName();
}
