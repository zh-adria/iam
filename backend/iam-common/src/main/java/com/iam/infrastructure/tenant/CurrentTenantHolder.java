package com.iam.infrastructure.tenant;

/**
 * Thread-local holder for the current tenant identifier. Set by a filter/interceptor
 * at request entry (from JWT `tenant` claim), cleared on exit.
 */
public final class CurrentTenantHolder {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private CurrentTenantHolder() {}

    public static void set(String tenantCode) { TENANT.set(tenantCode); }

    public static String get() { return TENANT.get(); }

    public static void clear() { TENANT.remove(); }
}
