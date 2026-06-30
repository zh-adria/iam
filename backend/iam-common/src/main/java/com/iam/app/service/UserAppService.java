package com.iam.app.service;

import com.iam.app.dto.ApiResult;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.*;
import com.iam.infrastructure.repository.*;
import com.iam.infrastructure.security.PasswordHasher;
import com.iam.infrastructure.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAppService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PermissionRepository permRepo;
    private final UserRoleRepository userRoleRepo;
    private final RolePermissionRepository rolePermRepo;
    private final PasswordHasher hasher;
    private final TotpService totp;

    @Transactional
    public Map<String, Object> register(String username, String password, String email, String phone, String tenant) {
        if (userRepo.existsByUsername(username)) {
            throw new AuthException("DUP_USERNAME", "用户名已存在");
        }
        UserEntity u = UserEntity.builder()
                .username(username)
                .passwordHash(hasher.encode(password))
                .email(email).phone(phone)
                .tenantCode(tenant == null ? "default" : tenant)
                .status(1).mfaEnabled(false).failCount(0)
                .build();
        u = userRepo.save(u);
        Map<String, Object> r = new HashMap<>();
        r.put("id", u.getId());
        r.put("username", u.getUsername());
        return r;
    }

    public Map<String, Object> profile(Long userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        List<String> roles = userRoleRepo.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleCode).collect(Collectors.toList());
        Map<String, Object> r = new HashMap<>();
        r.put("id", u.getId());
        r.put("username", u.getUsername());
        r.put("email", maskEmail(u.getEmail()));
        r.put("phone", maskPhone(u.getPhone()));
        r.put("tenant", u.getTenantCode());
        r.put("mfaEnabled", u.getMfaEnabled());
        r.put("roles", roles);
        return r;
    }

    @Transactional
    public Map<String, Object> setupMfa(Long userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        String secret = totp.generateSecret();
        u.setMfaSecret(secret);
        userRepo.save(u);
        Map<String, Object> r = new HashMap<>();
        r.put("secret", secret);
        r.put("otpauth", totp.otpAuthUri(secret, u.getUsername()));
        return r;
    }

    @Transactional
    public void confirmMfa(Long userId, String code) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        if (u.getMfaSecret() == null) throw new AuthException("NO_SECRET", "请先调用 setupMfa");
        if (!totp.verify(u.getMfaSecret(), code)) throw new AuthException("MFA_FAIL", "动态码错误");
        u.setMfaEnabled(true);
        userRepo.save(u);
    }

    @Transactional
    public void disableMfa(Long userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new AuthException("NOT_FOUND", "用户不存在"));
        u.setMfaEnabled(false);
        u.setMfaSecret(null);
        userRepo.save(u);
    }

    @Transactional
    public void assignRole(Long userId, String roleCode) {
        if (!userRepo.existsById(userId)) throw new AuthException("NOT_FOUND", "用户不存在");
        if (!roleRepo.findByCode(roleCode).isPresent()) throw new AuthException("ROLE_NOT_FOUND", "角色不存在");
        userRoleRepo.save(UserRoleEntity.builder().userId(userId).roleCode(roleCode).build());
    }

    @Transactional
    public void grantPermissionToRole(String roleCode, String permCode) {
        rolePermRepo.save(RolePermissionEntity.builder().roleCode(roleCode).permCode(permCode).build());
    }

    @Transactional
    public void createRole(String code, String name, String tenant) {
        if (roleRepo.findByCode(code).isPresent()) throw new AuthException("DUP_ROLE", "角色已存在");
        roleRepo.save(RoleEntity.builder().code(code).name(name).tenantCode(tenant).build());
    }

    @Transactional
    public void createPermission(String code, String type, String name, String resource, String action, String spel) {
        permRepo.save(PermissionEntity.builder()
                .code(code).type(type).name(name).resource(resource).action(action).spelExpression(spel).build());
    }

    public List<Map<String, Object>> listAudit(Long userId) {
        return userRepo.findById(userId).map(u -> java.util.Collections.<Map<String,Object>>emptyList()).orElseGet(java.util.Collections::emptyList);
    }

    public static String maskEmail(String e) {
        if (e == null || !e.contains("@")) return e;
        int at = e.indexOf('@');
        return e.charAt(0) + "***" + e.substring(at);
    }

    public static String maskPhone(String p) {
        if (p == null || p.length() < 7) return p;
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }
}
