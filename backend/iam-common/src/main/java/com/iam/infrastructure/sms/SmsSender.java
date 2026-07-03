package com.iam.infrastructure.sms;

/**
 * Pluggable SMS provider interface. Implementations: stub (console), Aliyun, Tencent.
 */
public interface SmsSender {

    /**
     * Send a verification code to the given phone number.
     *
     * @param phone target phone number
     * @param code  4-6 digit verification code
     * @return provider-specific message id (may be null for stub)
     * @throws Exception on provider error (caller handles as AuthException)
     */
    String send(String phone, String code) throws Exception;

    /** Provider name for logging / debugging. */
    String providerName();
}
