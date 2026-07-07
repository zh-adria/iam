package com.iam.start;

import com.iam.app.service.OAuth2ClientAppService;
import com.iam.app.service.UserAppService;
import com.iam.infrastructure.config.DynamicConfig;
import com.iam.infrastructure.entity.*;
import com.iam.infrastructure.repository.*;
import com.iam.infrastructure.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Idempotent demo seeder. Runs on every startup in 'dev'/'demo'/'test' profiles; creates demo
 * tenant/users/roles/permissions only if they don't already exist. Production starts with
 * {@code spring.profiles.active} excluding these profiles.
 *
 * ponytail: dev profile runs DemoSeeder so `dev.bat`/`dev.ps1` users get a working admin/Iam@2026
 * account out of the box. H2 is empty on first launch → without this, /login returns TENANT_NOT_FOUND.
 */
@Slf4j
@Component
@Profile({"dev", "demo", "test"})
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository perms;
    private final UserRoleRepository userRoles;
    private final RolePermissionRepository rolePerms;
    private final OAuth2ClientRepository clients;
    private final PasswordHasher hasher;
    private final DynamicConfig dynamicConfig;

    @Override
    public void run(String... args) {
        upsertTenant("default", "默认租户", "SHARED");
        upsertRole("ROLE_ADMIN", "管理员", "default");
        upsertRole("ROLE_USER", "普通用户", "default");
        upsertRole("ROLE_AUDITOR", "审计员", "default");

        upsertPerm("iam:user:assign-role", "API", "分配角色");
        upsertPerm("iam:user:create", "API", "创建用户");
        upsertPerm("iam:user:delete", "API", "删除用户");
        upsertPerm("iam:role:create", "API", "创建角色");
        upsertPerm("iam:role:grant", "API", "授权角色");
        upsertPerm("iam:permission:create", "API", "创建权限");
        upsertPerm("iam:permission:delete", "API", "删除权限");
        upsertPerm("iam:client:create", "API", "注册客户端");
        upsertPerm("iam:tenant:write", "API", "租户配置");
        upsertPerm("iam:config:read", "API", "读取系统配置");
        upsertPerm("iam:audit:read", "API", "读取审计");
        upsertPerm("iam:menu:dashboard", "MENU", "仪表盘");

        // ROLE_ADMIN：拥有全部 iam:* 级别的权限（管理员角色全权限）
        grant("ROLE_ADMIN", "iam:user:assign-role");
        grant("ROLE_ADMIN", "iam:user:create");
        grant("ROLE_ADMIN", "iam:user:delete");
        grant("ROLE_ADMIN", "iam:role:create");
        grant("ROLE_ADMIN", "iam:role:grant");
        grant("ROLE_ADMIN", "iam:permission:create");
        grant("ROLE_ADMIN", "iam:permission:delete");
        grant("ROLE_ADMIN", "iam:client:create");
        grant("ROLE_ADMIN", "iam:tenant:write");
        grant("ROLE_ADMIN", "iam:config:read");
        grant("ROLE_ADMIN", "iam:audit:read");
        grant("ROLE_ADMIN", "iam:menu:dashboard");
        grant("ROLE_USER", "iam:menu:dashboard");
        grant("ROLE_AUDITOR", "iam:menu:dashboard");

        Long adminId = upsertUser("admin", "Iam@2026", "admin@iam.local", "13800138000", "default");
        Long aliceId = upsertUser("alice", "User@2026", "alice@iam.local", "13900139000", "default");
        assignRole(adminId, "ROLE_ADMIN");
        assignRole(aliceId, "ROLE_USER");

        if (!clients.existsById("demo-client")) {
            clients.save(OAuth2ClientEntity.builder()
                    .clientId("demo-client")
                    .clientSecretHash(hasher.encode("demo-secret"))
                    .grantTypes("authorization_code,refresh_token,password,client_credentials")
                    .redirectUris("http://localhost:5173/callback,http://localhost:3000/callback")
                    .scopes("openid,profile")
                    .build());
        }

        // ponytail: seed default dynamic config for admin UI. drop-first clears table each dev start.
        dynamicConfig.seedIfAbsent(List.of(
                new DynamicConfig.ConfigValueSpec("iam.login.methods", "string", "password,sms,magic,social,sso,oauth2", "登录页展示方式，逗号分隔: password,sms,magic,social,sso,oauth2"),
                new DynamicConfig.ConfigValueSpec("iam.login.social-providers", "string", "wechat,alipay,qq,dingtalk,wecom", "登录页社交登录按钮，逗号分隔"),
                new DynamicConfig.ConfigValueSpec("iam.social.qq.app-id", "string", "", "QQ 开放平台 App ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.qq.app-secret", "secret", "", "QQ 开放平台 App Secret"),
                new DynamicConfig.ConfigValueSpec("iam.social.wechat.app-id", "string", "", "微信 App ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.wechat.app-secret", "secret", "", "微信 App Secret"),
                new DynamicConfig.ConfigValueSpec("iam.social.redirect-base-url", "string", "http://localhost:8080/iam/api/auth/social", "社交登录回调基础 URL"),
                new DynamicConfig.ConfigValueSpec("iam.social.alipay.app-id", "string", "", "支付宝 App ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.alipay.app-private-key", "secret", "", "支付宝应用私钥"),
                new DynamicConfig.ConfigValueSpec("iam.social.alipay.alipay-public-key", "secret", "", "支付宝公钥"),
                new DynamicConfig.ConfigValueSpec("iam.social.alipay.redirect-uri", "string", "", "支付宝回调地址"),
                new DynamicConfig.ConfigValueSpec("iam.social.dingtalk.app-id", "string", "", "钉钉 App ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.dingtalk.app-secret", "secret", "", "钉钉 App Secret"),
                new DynamicConfig.ConfigValueSpec("iam.social.wecom.corp-id", "string", "", "企微 Corp ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.wecom.agent-id", "string", "", "企微 Agent ID"),
                new DynamicConfig.ConfigValueSpec("iam.social.wecom.corp-secret", "secret", "", "企微 Corp Secret"),
                new DynamicConfig.ConfigValueSpec("iam.cas.server-url", "string", "", "CAS Server URL"),
                new DynamicConfig.ConfigValueSpec("iam.cas.service-url", "string", "http://localhost:8080/iam/api/auth/cas/callback", "CAS Service URL"),
                new DynamicConfig.ConfigValueSpec("iam.ldap.url", "string", "", "LDAP URL"),
                new DynamicConfig.ConfigValueSpec("iam.ldap.base", "string", "", "LDAP Base DN"),
                new DynamicConfig.ConfigValueSpec("iam.ldap.user-dn-pattern", "string", "uid={0},ou=people", "LDAP 用户 DN 模板"),
                new DynamicConfig.ConfigValueSpec("iam.ldap.manager-dn", "string", "", "LDAP Manager DN"),
                new DynamicConfig.ConfigValueSpec("iam.ldap.manager-password", "secret", "", "LDAP Manager Password"),
                new DynamicConfig.ConfigValueSpec("iam.sms.provider", "string", "stub", "短信服务商: stub/aliyun/tencent"),
                new DynamicConfig.ConfigValueSpec("iam.sms.code-ttl-seconds", "int", "300", "短信验证码有效期(秒)"),
                new DynamicConfig.ConfigValueSpec("iam.sms.code-length", "int", "6", "短信验证码长度"),
                new DynamicConfig.ConfigValueSpec("iam.sms.aliyun-access-key", "secret", "", "阿里云短信 AccessKey"),
                new DynamicConfig.ConfigValueSpec("iam.sms.aliyun-secret", "secret", "", "阿里云短信 Secret"),
                new DynamicConfig.ConfigValueSpec("iam.sms.aliyun-sign-name", "string", "", "阿里云短信签名"),
                new DynamicConfig.ConfigValueSpec("iam.sms.aliyun-template-code", "string", "", "阿里云短信模板"),
                new DynamicConfig.ConfigValueSpec("iam.sms.aliyun-endpoint", "string", "dysmsapi.aliyuncs.com", "阿里云短信 Endpoint"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.provider", "string", "stub", "Magic Link 服务商: stub/sendgrid"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.base-url", "string", "http://localhost:5173/magic-callback", "Magic Link 回调地址"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.ttl-minutes", "int", "15", "Magic Link 有效期(分钟)"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-host", "string", "", "SMTP Host"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-port", "int", "587", "SMTP Port"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-username", "string", "", "SMTP Username"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-password", "secret", "", "SMTP Password"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-from", "string", "", "SMTP From"),
                new DynamicConfig.ConfigValueSpec("iam.magic-link.smtp-starttls", "boolean", "true", "SMTP STARTTLS"),
                new DynamicConfig.ConfigValueSpec("iam.ratelimit.login-per-minute", "int", "10", "登录频率限制(次/分钟)"),
                new DynamicConfig.ConfigValueSpec("iam.jwt.access-ttl-minutes", "int", "30", "Access Token 有效期(分钟)"),
                new DynamicConfig.ConfigValueSpec("iam.password.max-fail-count", "int", "5", "密码最大失败次数"),
                new DynamicConfig.ConfigValueSpec("iam.password.lock-minutes", "int", "30", "账户锁定时长(分钟)")
        ));

        log.info("demo seed complete: admin/Iam@2026, alice/User@2026, client=demo-client/demo-secret");
    }

    private void upsertTenant(String code, String name, String mode) {
        if (!tenants.findByCode(code).isPresent()) {
            tenants.save(TenantEntity.builder().code(code).name(name).isolationMode(mode).build());
        }
    }
    private void upsertRole(String code, String name, String tenant) {
        if (!roles.findByCode(code).isPresent()) {
            roles.save(RoleEntity.builder().code(code).name(name).tenantCode(tenant).build());
        }
    }
    private void upsertPerm(String code, String type, String name) {
        if (!perms.findByCode(code).isPresent()) {
            perms.save(PermissionEntity.builder().code(code).type(type).name(name).build());
        }
    }
    private void grant(String role, String perm) {
        if (rolePerms.findByRoleCode(role).stream().noneMatch(rp -> rp.getPermCode().equals(perm))) {
            rolePerms.save(RolePermissionEntity.builder().roleCode(role).permCode(perm).build());
        }
    }
    private Long upsertUser(String username, String pwd, String email, String phone, String tenant) {
        Optional<UserEntity> existing = users.findByUsernameAndTenantCode(username, tenant);
        if (existing.isPresent()) return existing.get().getId();
        UserEntity u = users.save(UserEntity.builder()
                .username(username).passwordHash(hasher.encode(pwd))
                .email(email).phone(phone).tenantCode(tenant)
                .status(1).mfaEnabled(false).failCount(0).build());
        return u.getId();
    }
    private void assignRole(Long userId, String role) {
        if (userRoles.findByUserId(userId).stream().noneMatch(ur -> ur.getRoleCode().equals(role))) {
            userRoles.save(UserRoleEntity.builder().userId(userId).roleCode(role).build());
        }
    }
}
