package com.iam.start;

import com.iam.app.service.OAuth2ClientAppService;
import com.iam.app.service.UserAppService;
import com.iam.infrastructure.entity.*;
import com.iam.infrastructure.repository.*;
import com.iam.infrastructure.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Idempotent demo seeder. Runs on every startup; creates demo tenant/users/roles/permissions
 * only if they don't already exist. Safe to disable in production.
 * ponytail: hardcoded demo creds in code so login works out-of-the-box without manual hashing.
 */
@Slf4j
@Component
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

    @Override
    public void run(String... args) {
        upsertTenant("default", "默认租户", "SHARED");
        upsertRole("ROLE_ADMIN", "管理员", "default");
        upsertRole("ROLE_USER", "普通用户", "default");
        upsertRole("ROLE_AUDITOR", "审计员", "default");

        upsertPerm("iam:user:assign-role", "API", "分配角色");
        upsertPerm("iam:role:create", "API", "创建角色");
        upsertPerm("iam:role:grant", "API", "授权角色");
        upsertPerm("iam:permission:create", "API", "创建权限");
        upsertPerm("iam:client:create", "API", "注册客户端");
        upsertPerm("iam:menu:dashboard", "MENU", "仪表盘");

        grant("ROLE_ADMIN", "iam:user:assign-role");
        grant("ROLE_ADMIN", "iam:role:create");
        grant("ROLE_ADMIN", "iam:role:grant");
        grant("ROLE_ADMIN", "iam:permission:create");
        grant("ROLE_ADMIN", "iam:client:create");
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
