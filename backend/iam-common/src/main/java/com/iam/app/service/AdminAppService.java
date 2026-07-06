package com.iam.app.service;

import com.iam.app.dto.ApiResult;
import com.iam.domain.AuthException;
import com.iam.infrastructure.config.DynamicConfig;
import com.iam.infrastructure.entity.*;
import com.iam.infrastructure.repository.*;
import com.iam.infrastructure.security.PasswordHasher;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Admin-side service: CRUD for users/roles/permissions/tenants/clients + runtime config.
 * Lives in iam-common; wired only into the iam-admin service via AdminController.
 */
@Service
@RequiredArgsConstructor
public class AdminAppService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PermissionRepository permRepo;
    private final UserRoleRepository userRoleRepo;
    private final RolePermissionRepository rolePermRepo;
    private final TenantRepository tenantRepo;
    private final AuditLogRepository auditRepo;
    private final OAuth2ClientRepository clientRepo;
    private final SamlIdpRegistrationRepository samlRepo;
    private final DynamicConfig dynamicConfig;
    private final PasswordHasher hasher;
    private final com.iam.infrastructure.ldap.LdapConfig ldapConfig;
    private final ScimProvisionerTokenService scimTokenService;
    private final LdapGroupRoleMappingRepository ldapGroupMappingRepo;

    // ---------- users ----------
    public Map<String, Object> listUsers(int page, int size, String tenant) {
        List<UserEntity> all = userRepo.findAll();
        if (tenant != null && !tenant.isEmpty()) {
            all = all.stream().filter(u -> tenant.equals(u.getTenantCode())).collect(Collectors.toList());
        }
        int total = all.size();
        List<Map<String, Object>> rows = all.stream()
                .skip((long) (page - 1) * size).limit(size)
                .map(this::userRow)
                .collect(Collectors.toList());
        Map<String, Object> r = new HashMap<>();
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        r.put("rows", rows);
        return r;
    }

    @Transactional
    public Map<String, Object> createUser(String username, String password, String email, String phone,
                                          String tenant, int status) {
        if (userRepo.existsByUsername(username)) throw new AuthException("DUP_USERNAME", "用户名已存在");
        UserEntity u = userRepo.save(UserEntity.builder()
                .username(username).passwordHash(hasher.encode(password))
                .email(email).phone(phone).tenantCode(tenant == null ? "default" : tenant)
                .status(status).mfaEnabled(false).failCount(0).build());
        return userRow(u);
    }

    @Transactional
    public void resetPassword(Long userId, String newPwd) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        u.setPasswordHash(hasher.encode(newPwd));
        userRepo.save(u);
    }

    @Transactional
    public void setUserStatus(Long userId, int status) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        u.setStatus(status);
        userRepo.save(u);
    }

    @Transactional
    public void unlockUser(Long userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        u.setFailCount(0);
        u.setLockedUntil(null);
        userRepo.save(u);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepo.deleteById(userId);
    }

    private Map<String, Object> userRow(UserEntity u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", UserAppService.maskEmail(u.getEmail()));
        m.put("phone", UserAppService.maskPhone(u.getPhone()));
        m.put("tenant", u.getTenantCode());
        m.put("status", u.getStatus());
        m.put("mfaEnabled", u.getMfaEnabled());
        m.put("roles", userRoleRepo.findByUserId(u.getId()).stream()
                .map(UserRoleEntity::getRoleCode).collect(Collectors.toList()));
        return m;
    }

    // ---------- roles ----------
    public Page<Map<String, Object>> listRoles(int page, int size, String tenant) {
        Pageable p = PageRequest.of(Math.max(page - 1, 0), size);
        List<RoleEntity> filtered = roleRepo.findAll().stream()
                .filter(r -> tenant == null || tenant.isEmpty() || tenant.equals(r.getTenantCode()))
                .collect(Collectors.toList());
        int total = filtered.size();
        List<Map<String, Object>> rows = filtered.stream()
                .skip(p.getOffset()).limit(p.getPageSize())
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", r.getCode());
                    m.put("name", r.getName());
                    m.put("tenant", r.getTenantCode());
                    return m;
                }).collect(Collectors.toList());
        return new PageImpl<>(rows, p, total);
    }

    @Transactional
    public void createRole(String code, String name, String tenant) {
        if (roleRepo.findByCode(code).isPresent()) throw new AuthException("DUP_ROLE", "角色已存在");
        roleRepo.save(RoleEntity.builder().code(code).name(name).tenantCode(tenant == null ? "default" : tenant).build());
    }

    @Transactional
    public void deleteRole(String code) {
        roleRepo.findByCode(code).ifPresent(roleRepo::delete);
    }

    @Transactional
    public void assignRole(Long userId, String roleCode) {
        if (!userRepo.existsById(userId)) throw new AuthException("NOT_FOUND", "用户不存在");
        if (!roleRepo.findByCode(roleCode).isPresent()) throw new AuthException("ROLE_NOT_FOUND", "角色不存在");
        userRoleRepo.save(UserRoleEntity.builder().userId(userId).roleCode(roleCode).build());
    }

    @Transactional
    public void revokeRole(Long userId, String roleCode) {
        userRoleRepo.findByUserId(userId).stream()
                .filter(ur -> ur.getRoleCode().equals(roleCode))
                .forEach(userRoleRepo::delete);
    }

    // ---------- permissions ----------
    public Page<Map<String, Object>> listPermissions(int page, int size) {
        Pageable p = PageRequest.of(Math.max(page - 1, 0), size);
        List<PermissionEntity> all = permRepo.findAll();
        int total = all.size();
        List<Map<String, Object>> rows = all.stream()
                .skip(p.getOffset()).limit(p.getPageSize())
                .map(pe -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", pe.getCode());
                    m.put("type", pe.getType());
                    m.put("name", pe.getName());
                    m.put("resource", pe.getResource());
                    m.put("action", pe.getAction());
                    m.put("spel", pe.getSpelExpression());
                    return m;
                }).collect(Collectors.toList());
        return new PageImpl<>(rows, p, total);
    }

    @Transactional
    public void createPermission(String code, String type, String name, String resource, String action, String spel) {
        permRepo.save(PermissionEntity.builder()
                .code(code).type(type).name(name).resource(resource).action(action).spelExpression(spel).build());
    }

    @Transactional
    public void deletePermission(String code) {
        permRepo.findByCode(code).ifPresent(permRepo::delete);
    }

    @Transactional
    public void grantPermission(String roleCode, String permCode) {
        if (rolePermRepo.findByRoleCode(roleCode).stream().noneMatch(rp -> rp.getPermCode().equals(permCode))) {
            rolePermRepo.save(RolePermissionEntity.builder().roleCode(roleCode).permCode(permCode).build());
        }
    }

    @Transactional
    public void revokePermission(String roleCode, String permCode) {
        rolePermRepo.findByRoleCode(roleCode).stream()
                .filter(rp -> rp.getPermCode().equals(permCode))
                .forEach(rolePermRepo::delete);
    }

    public List<String> listRolePermissions(String roleCode) {
        return rolePermRepo.findByRoleCode(roleCode).stream()
                .map(RolePermissionEntity::getPermCode)
                .collect(Collectors.toList());
    }

    // ---------- tenants ----------
    public Page<Map<String, Object>> listTenants(int page, int size) {
        Pageable p = PageRequest.of(Math.max(page - 1, 0), size);
        List<TenantEntity> all = tenantRepo.findAll();
        int total = all.size();
        List<Map<String, Object>> rows = all.stream()
                .skip(p.getOffset()).limit(p.getPageSize())
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.getId());
                    m.put("code", t.getCode());
                    m.put("name", t.getName());
                    m.put("isolationMode", t.getIsolationMode());
                    m.put("schemaName", t.getSchemaName());
                    m.put("ldapUrl", t.getLdapUrl());
                    m.put("ldapBase", t.getLdapBase());
                    m.put("enabled", t.getEnabled());
                    return m;
                }).collect(Collectors.toList());
        return new PageImpl<>(rows, p, total);
    }

    @Transactional
    public void upsertTenant(String code, String name, String mode, String ldapUrl, String ldapBase, Boolean enabled) {
        TenantEntity t = tenantRepo.findByCode(code).orElseGet(() ->
                TenantEntity.builder().code(code).name(name == null ? code : name)
                        .isolationMode(mode == null ? "SHARED" : mode).build());
        if (name != null) t.setName(name);
        if (mode != null) t.setIsolationMode(mode);
        if (ldapUrl != null) t.setLdapUrl(ldapUrl);
        if (ldapBase != null) t.setLdapBase(ldapBase);
        if (enabled != null) t.setEnabled(enabled);
        tenantRepo.save(t);
    }

    @Transactional
    public void deleteTenant(String code) {
        TenantEntity t = tenantRepo.findByCode(code).orElseThrow();
        long userCount = userRepo.countByTenantCode(code);
        if (userCount > 0) {
            throw new IllegalStateException("租户下仍有 " + userCount + " 个用户，请先删除或迁移用户");
        }
        tenantRepo.delete(t);
    }

    // ---------- oauth2 clients ----------
    public Page<Map<String, Object>> listClients(int page, int size) {
        Pageable p = PageRequest.of(Math.max(page - 1, 0), size);
        List<OAuth2ClientEntity> all = clientRepo.findAll();
        int total = all.size();
        List<Map<String, Object>> rows = all.stream()
                .skip(p.getOffset()).limit(p.getPageSize())
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("clientId", c.getClientId());
                    m.put("grantTypes", c.getGrantTypes());
                    m.put("redirectUris", c.getRedirectUris());
                    m.put("scopes", c.getScopes());
                    m.put("createdAt", c.getCreatedAt());
                    m.put("accessTokenTtlMinutes", c.getAccessTokenTtlMinutes());
                    m.put("refreshTokenTtlDays", c.getRefreshTokenTtlDays());
                    m.put("autoApprove", c.getAutoApprove());
                    m.put("idTokenClaims", c.getIdTokenClaims());
                    return m;
                }).collect(Collectors.toList());
        return new PageImpl<>(rows, p, total);
    }

    @Transactional
    public void upsertClient(String clientId, String clientSecret, String grantTypes,
                             String redirectUris, String scopes,
                             Integer accessTokenTtlMinutes, Integer refreshTokenTtlDays,
                             Boolean autoApprove, String idTokenClaims) {
        OAuth2ClientEntity c = clientRepo.findById(clientId).orElseGet(() ->
                OAuth2ClientEntity.builder().clientId(clientId).build());
        if (clientSecret != null && !clientSecret.isEmpty()) c.setClientSecretHash(hasher.encode(clientSecret));
        if (grantTypes != null) c.setGrantTypes(grantTypes);
        if (redirectUris != null) c.setRedirectUris(redirectUris);
        if (scopes != null) c.setScopes(scopes);
        if (accessTokenTtlMinutes != null) c.setAccessTokenTtlMinutes(accessTokenTtlMinutes);
        if (refreshTokenTtlDays != null) c.setRefreshTokenTtlDays(refreshTokenTtlDays);
        if (autoApprove != null) c.setAutoApprove(autoApprove);
        if (idTokenClaims != null) c.setIdTokenClaims(idTokenClaims);
        clientRepo.save(c);
    }

    @Transactional
    public void deleteClient(String clientId) {
        clientRepo.deleteById(clientId);
    }

    // ---------- audit ----------
    public Map<String, Object> listAudit(int page, int size, Long userId) {
        List<AuditLogEntity> all = userId != null
                ? auditRepo.findByUserIdOrderByOccurredAtDesc(userId)
                : auditRepo.findAll();
        List<AuditLogEntity> sorted = all.stream()
                .sorted((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()))
                .collect(Collectors.toList());
        int total = sorted.size();
        List<Map<String, Object>> rows = sorted.stream()
                .skip((long) (page - 1) * size).limit(size)
                .map(this::auditRow)
                .collect(Collectors.toList());
        Map<String, Object> r = new HashMap<>();
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        r.put("rows", rows);
        return r;
    }

    private Map<String, Object> auditRow(AuditLogEntity a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("tenant", a.getTenantCode());
        m.put("action", a.getAction());
        m.put("result", a.getResult());
        m.put("principal", a.getPrincipal());
        m.put("ip", a.getIp());
        m.put("detail", a.getDetail());
        m.put("occurredAt", a.getOccurredAt());
        m.put("prevHash", a.getHashChainPrev());
        return m;
    }

    // ---------- SAML IdP registrations ----------
    public List<Map<String, Object>> listSamlIdps(String tenant) {
        List<SamlIdpRegistrationEntity> all = tenant == null || tenant.isEmpty()
                ? samlRepo.findAll() : samlRepo.findByTenantCode(tenant);
        return all.stream().map(this::samlRow).collect(Collectors.toList());
    }

    @Transactional
    public void upsertSamlIdp(String tenantCode, String registrationId, String idpEntityId,
                              String idpSsoUrl, String idpMetadataUrl, String idpMetadataXml,
                              String spEntityId, String acsTemplate, Boolean enabled,
                              String signingCertPem, String encryptionCertPem,
                              String nameIdFormat, String attributeMapping) {
        SamlIdpRegistrationEntity e = samlRepo.findByTenantCodeAndRegistrationId(tenantCode, registrationId)
                .orElseGet(() -> SamlIdpRegistrationEntity.builder()
                        .tenantCode(tenantCode).registrationId(registrationId).build());
        if (idpEntityId != null) e.setIdpEntityId(idpEntityId);
        if (idpSsoUrl != null) e.setIdpSsoUrl(idpSsoUrl);
        if (idpMetadataUrl != null) e.setIdpMetadataUrl(idpMetadataUrl);
        if (idpMetadataXml != null) e.setIdpMetadataXml(idpMetadataXml);
        if (spEntityId != null) e.setSpEntityId(spEntityId);
        if (acsTemplate != null) e.setAcsTemplate(acsTemplate);
        if (enabled != null) e.setEnabled(enabled);
        if (signingCertPem != null) e.setSigningCertPem(signingCertPem);
        if (encryptionCertPem != null) e.setEncryptionCertPem(encryptionCertPem);
        if (nameIdFormat != null) e.setNameIdFormat(nameIdFormat);
        if (attributeMapping != null) e.setAttributeMapping(attributeMapping);
        samlRepo.save(e);
    }

    @Transactional
    public void deleteSamlIdp(String tenantCode, String registrationId) {
        samlRepo.findByTenantCodeAndRegistrationId(tenantCode, registrationId).ifPresent(samlRepo::delete);
    }

    private Map<String, Object> samlRow(SamlIdpRegistrationEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("tenantCode", e.getTenantCode());
        m.put("registrationId", e.getRegistrationId());
        m.put("idpEntityId", e.getIdpEntityId());
        m.put("idpSsoUrl", e.getIdpSsoUrl());
        m.put("idpMetadataUrl", e.getIdpMetadataUrl());
        m.put("spEntityId", e.getSpEntityId());
        m.put("acsTemplate", e.getAcsTemplate());
        m.put("enabled", e.getEnabled());
        m.put("signingCertPem", e.getSigningCertPem());
        m.put("encryptionCertPem", e.getEncryptionCertPem());
        m.put("nameIdFormat", e.getNameIdFormat());
        m.put("attributeMapping", e.getAttributeMapping());
        return m;
    }

    // ---------- runtime config (LDAP/SAML/Social/MFA) ----------
    public Map<String, Object> systemConfig() {
        Map<String, Object> m = new HashMap<>();
        m.put("items", dynamicConfig.listAll());
        return m;
    }

    public void setSystemConfig(String key, String value, String type) {
        dynamicConfig.set(key, value == null ? "" : value, type == null || type.isBlank() ? "string" : type);
    }

    public void deleteSystemConfig(String key) {
        dynamicConfig.remove(key);
    }

    // ---------- SCIM tokens ----------
    public List<Map<String, Object>> listScimTokens() {
        return scimTokenService.listAll();
    }

    public Map<String, Object> createScimToken(String name, String tenantCode, String scope, long ttlDays) {
        ScimTokenEntity e = scimTokenService.createToken(name, tenantCode, scope, ttlDays);
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("tokenPrefix", e.getTokenPrefix());
        m.put("token", e.getTokenHash()); // raw token, shown once
        m.put("tenantCode", e.getTenantCode());
        m.put("scope", e.getScope());
        m.put("enabled", e.getEnabled());
        m.put("expiresAt", e.getExpiresAt());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    public void revokeScimToken(Long id) {
        scimTokenService.revoke(id);
    }

    // ---------- LDAP ----------
    public Map<String, Object> testLdapConnection(String url, String base, String managerDn, String managerPassword, boolean useSsl) {
        Map<String, Object> r = new HashMap<>();
        try {
            String ldapUrl = (url != null && !url.isEmpty()) ? url : ldapConfig.systemUrl();
            if (ldapUrl == null || ldapUrl.isEmpty()) {
                r.put("success", false);
                r.put("message", "LDAP URL 未配置");
                return r;
            }
            String ldapBase = (base != null && !base.isEmpty()) ? base : "";
            org.springframework.ldap.core.LdapTemplate template = ldapConfig.templateFor(ldapUrl, ldapBase);
            if (managerDn != null && !managerDn.isEmpty() && managerPassword != null) {
                // authenticated bind
                javax.naming.directory.DirContext ctx = template.getContextSource().getContext(managerDn, managerPassword);
                ctx.close();
            } else {
                // anonymous search
                template.search("", "(objectClass=*)", new org.springframework.ldap.core.AttributesMapper<Object>() {
                    @Override
                    public Object mapFromAttributes(javax.naming.directory.Attributes attrs) {
                        return null;
                    }
                });
            }
            // Save config on successful connection
            dynamicConfig.set("iam.ldap.url", ldapUrl, "string");
            dynamicConfig.set("iam.ldap.base", ldapBase, "string");
            if (managerDn != null && !managerDn.isEmpty()) {
                dynamicConfig.set("iam.ldap.manager-dn", managerDn, "string");
                dynamicConfig.set("iam.ldap.manager-password", managerPassword, "secret");
            }
            r.put("success", true);
            r.put("message", "连接成功");
        } catch (Exception e) {
            r.put("success", false);
            r.put("message", "连接失败: " + e.getMessage());
        }
        return r;
    }

    // ---------- LDAP group-to-role mapping ----------
    public List<Map<String, Object>> listLdapGroupMappings(String tenant) {
        String tc = tenant == null || tenant.isEmpty() ? "default" : tenant;
        return ldapGroupMappingRepo.findByTenantCode(tc).stream().map(m -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", m.getId());
            r.put("tenantCode", m.getTenantCode());
            r.put("ldapGroupDn", m.getLdapGroupDn());
            r.put("roleCode", m.getRoleCode());
            return r;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> upsertLdapGroupMapping(String tenantCode, String ldapGroupDn, String roleCode) {
        var existing = ldapGroupMappingRepo.findByTenantCodeAndLdapGroupDn(tenantCode, ldapGroupDn);
        var m = existing.stream().findFirst().orElseGet(() ->
                com.iam.infrastructure.entity.LdapGroupRoleMappingEntity.builder()
                        .tenantCode(tenantCode).ldapGroupDn(ldapGroupDn).build());
        m.setRoleCode(roleCode);
        ldapGroupMappingRepo.save(m);
        Map<String, Object> r = new HashMap<>();
        r.put("id", m.getId());
        r.put("tenantCode", m.getTenantCode());
        r.put("ldapGroupDn", m.getLdapGroupDn());
        r.put("roleCode", m.getRoleCode());
        return r;
    }

    @Transactional
    public void deleteLdapGroupMapping(String tenantCode, String ldapGroupDn) {
        ldapGroupMappingRepo.deleteByTenantCodeAndLdapGroupDn(tenantCode, ldapGroupDn);
    }
}
