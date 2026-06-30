package com.iam.infrastructure.ldap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.annotation.PostConstruct;

/**
 * LDAP context source wired from iam.ldap.* config.
 * ponytail: single LDAP server for all tenants — for per-tenant LDAP, build LdapContextSource per TenantEntity.ldapUrl.
 */
@Slf4j
@Configuration
public class LdapConfig {

    @Value("${iam.ldap.url:}")
    private String url;
    @Value("${iam.ldap.base:}")
    private String base;
    @Value("${iam.ldap.user-dn-pattern:uid={0},ou=people}")
    private String userDnPattern;
    @Value("${iam.ldap.manager-dn:}")
    private String managerDn;
    @Value("${iam.ldap.manager-password:}")
    private String managerPassword;

    private LdapTemplate ldapTemplate;
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (url == null || url.isEmpty()) {
            log.info("LDAP not configured (iam.ldap.url empty) — LDAP auth disabled");
            this.enabled = false;
            return;
        }
        LdapContextSource cs = new LdapContextSource();
        cs.setUrl(url);
        cs.setBase(base);
        if (managerDn != null && !managerDn.isEmpty()) {
            cs.setUserDn(managerDn);
            cs.setPassword(managerPassword);
        }
        cs.afterPropertiesSet();
        this.ldapTemplate = new LdapTemplate(cs);
        this.enabled = true;
        log.info("LDAP context source initialized: url={} base={}", url, base);
    }

    public LdapTemplate template() { return ldapTemplate; }
    public String userDnPattern() { return userDnPattern; }
    public boolean isEnabled() { return enabled; }

    /**
     * Reconfigure at runtime (test helper / dynamic tenant config).
     */
    public void reconfigure(String url, String base, String userDnPattern,
                            String managerDn, String managerPassword) {
        this.url = url;
        this.base = base;
        this.userDnPattern = userDnPattern;
        this.managerDn = managerDn;
        this.managerPassword = managerPassword;
        init();
    }
}
