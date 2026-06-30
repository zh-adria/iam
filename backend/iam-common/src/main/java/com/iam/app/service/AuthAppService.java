package com.iam.app.service;

import com.iam.app.dto.*;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.*;
import com.iam.infrastructure.repository.*;
import com.iam.infrastructure.security.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAppService {

    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final RolePermissionRepository rolePermRepo;
    private final OAuth2ClientRepository clientRepo;
    private final RefreshTokenRepository refreshRepo;
    private final TenantRepository tenantRepo;
    private final PasswordHasher hasher;
    private final TotpService totp;
    private final JwtTokenService jwt;
    private final TokenCacheService cache;
    private final AuditLogService auditSvc;
    private final LoginFailureRecorder failureRecorder;

    @Value("${iam.password.max-fail-count:5}")
    private int maxFail;
    @Value("${iam.password.lock-minutes:30}")
    private int lockMin;

    @Transactional
    public TokenResponse login(LoginCommand cmd) {
        if (!cache.tryAcquireLogin(cmd.getIp() == null ? "unknown" : cmd.getIp())) {
            throw new AuthException("RATE_LIMITED", "登录过于频繁，请稍后再试");
        }
        String tenant = cmd.getTenantCode() == null ? "default" : cmd.getTenantCode();
        ensureTenant(tenant);

        UserEntity user = userRepo.findByUsernameAndTenantCode(cmd.getUsername(), tenant)
                .orElseThrow(() -> auditFail(null, tenant, cmd, "USER_NOT_FOUND", "用户不存在"));
        if (user.getStatus() == 2 || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            throw new AuthException("LOCKED", "账号已锁定，请稍后重试");
        }
        if (!hasher.matches(cmd.getPassword(), user.getPasswordHash())) {
            failureRecorder.registerFailure(user);
            throw auditFail(user.getId(), tenant, cmd, "BAD_CREDENTIALS", "用户名或密码错误");
        }

        // reset failures
        user.setFailCount(0);
        user.setLockedUntil(null);
        userRepo.save(user);

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            // issue short-lived mfa token, no real access token yet
            String mfaToken = issueMfaToken(user.getId());
            auditSvc.record(user.getId(), tenant, "LOGIN_MFA_PENDING", "SUCCESS", user.getUsername(), cmd.getIp(), "MFA required");
            return TokenResponse.builder().mfaRequired(true).mfaToken(mfaToken).build();
        }

        return issueTokens(user, "iam-self", Collections.emptyList());
    }

    @Transactional
    public TokenResponse verifyMfa(String mfaToken, String code, String ip) {
        Claims c;
        try {
            c = Jwts.parserBuilder()
                    .setSigningKey(jwtKey())
                    .build()
                    .parseClaimsJws(mfaToken).getBody();
        } catch (Exception e) {
            throw new AuthException("MFA_TOKEN_INVALID", "MFA会话已过期");
        }
        if (!"mfa".equals(c.get("typ"))) throw new AuthException("MFA_TOKEN_INVALID", "MFA token类型错误");
        Long userId = c.get("uid", Long.class);
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        if (!totp.verify(user.getMfaSecret(), code)) {
            auditSvc.record(userId, user.getTenantCode(), "MFA_FAIL", "FAIL", user.getUsername(), ip, "wrong TOTP");
            throw new AuthException("MFA_FAIL", "动态码错误");
        }
        auditSvc.record(userId, user.getTenantCode(), "MFA_PASS", "SUCCESS", user.getUsername(), ip, "TOTP ok");
        return issueTokens(user, "iam-self", Collections.emptyList());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, String clientId) {
        RefreshTokenEntity rt = refreshRepo.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException("INVALID_REFRESH", "刷新令牌无效"));
        if (rt.getRevokedAt() != null || rt.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("EXPIRED_REFRESH", "刷新令牌已失效");
        }
        rt.setRevokedAt(Instant.now());
        refreshRepo.save(rt); // rotate
        UserEntity user = userRepo.findById(rt.getUserId())
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "用户不存在"));
        auditSvc.record(user.getId(), rt.getTenantCode(), "TOKEN_REFRESH", "SUCCESS", user.getUsername(), null, "refresh");
        return issueTokens(user, rt.getClientId(), parseScopes(rt.getScopes()));
    }

    @Transactional
    public void logout(String accessToken, String ip) {
        try {
            Claims c = jwt.parse(accessToken);
            String jti = (String) c.get("jti");
            if (jti != null) cache.revokeAccess(jti);
            auditSvc.record(c.get("uid", Long.class), c.get("tenant", String.class), "LOGOUT", "SUCCESS", c.getSubject(), ip, "logout");
        } catch (Exception ignored) { }
    }

    // ---------- helpers ----------

    private TokenResponse issueTokens(UserEntity user, String clientId, List<String> scopeFilter) {
        List<UserRoleEntity> urs = userRoleRepo.findByUserId(user.getId());
        List<String> roleCodes = urs.stream().map(UserRoleEntity::getRoleCode).collect(Collectors.toList());
        List<String> perms = roleCodes.isEmpty() ? Collections.emptyList() : rolePermRepo.findPermCodesByRoleCodes(roleCodes);

        String jti = java.util.UUID.randomUUID().toString();
        String access = jwt.issueAccess(jti, user.getId(), user.getUsername(), user.getTenantCode(), roleCodes, perms, clientId);
        cache.cacheAccess(jti, user.getUsername(), jwt.accessTtlSec());

        String refreshToken = UUID.randomUUID().toString().replace("-", "") + "." + Long.toHexString(user.getId());
        RefreshTokenEntity rt = RefreshTokenEntity.builder()
                .token(refreshToken).userId(user.getId()).tenantCode(user.getTenantCode())
                .clientId(clientId).scopes(String.join(",", perms))
                .expiresAt(Instant.now().plusSeconds(jwt.refreshTtlSec()))
                .build();
        refreshRepo.save(rt);

        auditSvc.record(user.getId(), user.getTenantCode(), "TOKEN_ISSUE", "SUCCESS", user.getUsername(), null, "login");

        return TokenResponse.builder()
                .accessToken(access).refreshToken(refreshToken).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .roles(roleCodes).permissions(perms)
                .build();
    }

    private String issueMfaToken(Long userId) {
        // ponytail: reuse JWT secret to sign a 5-min MFA continuation token.
        return Jwts.builder()
                .claim("uid", userId).claim("typ", "mfa")
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(jwtKey()).compact();
    }

    private javax.crypto.SecretKey jwtKey() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                System.getenv().getOrDefault("IAM_JWT_SECRET",
                        "CHANGE_ME_super_secret_at_least_32_bytes_long_for_HS256").getBytes());
    }

    private void ensureTenant(String tenant) {
        if (!tenantRepo.findByCode(tenant).isPresent()) {
            throw new AuthException("TENANT_NOT_FOUND", "租户不存在");
        }
    }

    private List<String> parseScopes(String s) {
        if (s == null || s.isEmpty()) return Collections.emptyList();
        return Arrays.asList(s.split(","));
    }

    private AuthException auditFail(Long uid, String tenant, LoginCommand cmd, String code, String msg) {
        auditSvc.record(uid, tenant, "LOGIN", "FAIL", cmd.getUsername(), cmd.getIp(), code + ":" + msg);
        return new AuthException(code, msg);
    }
}
