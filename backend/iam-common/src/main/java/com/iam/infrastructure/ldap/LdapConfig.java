package com.iam.infrastructure.ldap;

import com.iam.infrastructure.config.DynamicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LDAP context source — now a factory: builds LdapTemplate on demand per requested
 * (url, base) pair. System default from iam.ldap.* config; per-tenant override
 * from TenantEntity.ldapUrl / ldapBase falls through tenant-aware path in LdapAuthService.
 */
@Slf4j
@Configuration
public class LdapConfig {

    private final DynamicConfig dynamicConfig;

    public LdapConfig(DynamicConfig dynamicConfig) {
        this.dynamicConfig = dynamicConfig;
    }

    @Value("${iam.ldap.url:}")
    private String sysUrl = "";
    @Value("${iam.ldap.base:}")
    private String sysBase = "";
    @Value("${iam.ldap.user-dn-pattern:uid={0},ou=people}")
    private String sysUserDnPattern = "uid={0},ou=people";
    @Value("${iam.ldap.user-search-filter:(uid={0})}")
    private String sysUserSearchFilter = "(uid={0})";
    @Value("${iam.ldap.attribute-mapping:mail=email,cn=displayName}")
    private String sysAttributeMapping = "mail=email,cn=displayName";
    @Value("${iam.ldap.manager-dn:}")
    private String sysManagerDn = "";
    @Value("${iam.ldap.manager-password:}")
    private String sysManagerPassword = "";

    /** Cache of LdapTemplate keyed by "url|base" — one per tenant LDAP server. */
    private final Map<String, LdapTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, String> userDnPatterns = new ConcurrentHashMap<>();
    private final Map<String, String> baseByUrl = new ConcurrentHashMap<>();

    private LdapTemplate sysTemplate;
    private boolean sysEnabled;

    @PostConstruct
    public void init() {
        applyDynamicConfig();
        sysEnabled = sysUrl != null && !sysUrl.isEmpty();
        if (sysEnabled) {
            sysTemplate = build(sysUrl, sysBase, sysManagerDn, sysManagerPassword);
            baseByUrl.put(sysUrl, sysBase);
            log.info("LDAP system default initialized: url={} base={}", sysUrl, sysBase);
        } else {
            log.info("LDAP not configured (iam.ldap.url empty) — system LDAP disabled, per-tenant still possible");
        }
        dynamicConfig.onChange((key, value) -> {
            if (key != null && key.startsWith("iam.ldap.")) {
                reconfigureFromDynamic();
            }
        });
    }

    /** System-level default template. */
    public LdapTemplate systemTemplate() { return sysTemplate; }

    public String systemUserDnPattern() { return sysUserDnPattern; }

    public String systemUserSearchFilter() { return sysUserSearchFilter; }

    public String systemAttributeMapping() { return sysAttributeMapping; }

    public boolean isSystemEnabled() { return sysEnabled; }

    public String systemUrl() { return sysUrl; }

    /**
     * Get (or create) a LdapTemplate for the given LDAP server.
     * Falls back to system default when tenant has no dedicated config.
     */
    public LdapTemplate templateFor(String url, String base) {
        if (url == null || url.isEmpty()) return sysTemplate;
        baseByUrl.put(url, base == null ? "" : base);
        String key = url + "|" + base;
        return templates.computeIfAbsent(key, k -> build(url, base, null, null));
    }

    public String baseFor(String url) {
        return baseByUrl.getOrDefault(url, sysBase);
    }

    public String userDnPatternFor(String url) {
        return userDnPatterns.getOrDefault(url, sysUserDnPattern);
    }

    public void setUserDnPatternFor(String url, String pattern) {
        userDnPatterns.put(url, pattern);
    }

    /**
     * Runtime reconfiguration — used when a tenant config is created/updated or in tests.
     * Recreates the system-level template.
     */
    public void reconfigure(String url, String base, String userDnPattern, String managerDn, String managerPassword, String userSearchFilter) {
        this.sysUrl = url;
        this.sysBase = base;
        this.sysUserDnPattern = userDnPattern;
        this.sysManagerDn = managerDn;
        this.sysManagerPassword = managerPassword;
        if (userSearchFilter != null) this.sysUserSearchFilter = userSearchFilter;
        refreshSystemTemplate();
    }

    private void reconfigureFromDynamic() {
        applyDynamicConfig();
        refreshSystemTemplate();
    }

    private void applyDynamicConfig() {
        this.sysUrl = dynamicConfig.getString("iam.ldap.url", sysUrl == null ? "" : sysUrl);
        this.sysBase = dynamicConfig.getString("iam.ldap.base", sysBase == null ? "" : sysBase);
        this.sysUserDnPattern = dynamicConfig.getString("iam.ldap.user-dn-pattern",
                sysUserDnPattern == null || sysUserDnPattern.isEmpty() ? "uid={0},ou=people" : sysUserDnPattern);
        this.sysUserSearchFilter = dynamicConfig.getString("iam.ldap.user-search-filter",
                sysUserSearchFilter == null || sysUserSearchFilter.isEmpty() ? "(uid={0})" : sysUserSearchFilter);
        this.sysManagerDn = dynamicConfig.getString("iam.ldap.manager-dn", sysManagerDn == null ? "" : sysManagerDn);
        this.sysManagerPassword = dynamicConfig.getString("iam.ldap.manager-password",
                sysManagerPassword == null ? "" : sysManagerPassword);
    }

    private void refreshSystemTemplate() {
        templates.clear();
        baseByUrl.clear();
        sysEnabled = sysUrl != null && !sysUrl.isEmpty();
        if (sysEnabled) {
            sysTemplate = build(sysUrl, sysBase, sysManagerDn, sysManagerPassword);
            baseByUrl.put(sysUrl, sysBase);
            log.info("LDAP system default refreshed: url={} base={}", sysUrl, sysBase);
        } else {
            sysTemplate = null;
            log.info("LDAP system default disabled");
        }
    }

    private LdapTemplate build(String url, String base, String managerDn, String managerPw) {
        LdapContextSource cs = new LdapContextSource();
        cs.setUrl(url);
        cs.setBase(base == null || base.isEmpty() ? "" : base);
        if (managerDn != null && !managerDn.isEmpty()) {
            cs.setUserDn(managerDn);
            cs.setPassword(managerPw);
        }
        cs.afterPropertiesSet();
        return new LdapTemplate(cs);
    }
}
