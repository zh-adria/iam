package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.SocialBindingEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.ldap.LdapConfig;
import com.iam.infrastructure.repository.SocialBindingRepository;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.directory.Attributes;
import java.util.List;

/**
 * LDAP/AD authentication + user provisioning.
 * On successful bind: find-or-create local user linked via SocialBindingEntity(provider='ldap').
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LdapAuthService {

    private final LdapConfig ldap;
    private final UserRepository userRepo;
    private final SocialBindingRepository socialRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;

    @Transactional
    public TokenResponse authenticate(String username, String password, String tenantCode, String ip) {
        if (!ldap.isEnabled()) throw new AuthException("LDAP_DISABLED", "LDAP 未配置");
        LdapTemplate t = ldap.template();
        String dn = ldap.userDnPattern().replace("{0}", username);

        boolean ok;
        try {
            // ponytail: returns boolean (true=bind ok). Newer query-based overload throws on failure.
            ok = t.authenticate("", "(uid=" + username + ")", password);
        } catch (AuthenticationException e) {
            ok = false;
        } catch (Exception e) {
            log.error("LDAP auth error", e);
            throw new AuthException("LDAP_ERROR", "LDAP 认证失败: " + e.getMessage());
        }
        if (!ok) {
            audit.record(null, tenantCode, "LDAP_LOGIN", "FAIL", username, ip, "bad credentials");
            throw new AuthException("BAD_CREDENTIALS", "LDAP 用户名或密码错误");
        }

        // fetch attributes for provisioning
        String email = null, displayName = username;
        try {
            List<java.util.Map<String,String>> attrs = t.search(
                    "", "(uid=" + username + ")",
                    (AttributesMapper<java.util.Map<String,String>>) this::mapAttrs);
            if (!attrs.isEmpty()) {
                email = attrs.get(0).get("mail");
                String cn = attrs.get(0).get("cn");
                if (cn != null) displayName = cn;
            }
        } catch (Exception e) {
            log.warn("LDAP attribute fetch failed for {}: {}", username, e.getMessage());
        }
        final String emailFinal = email;
        final String displayFinal = displayName;

        UserEntity user = socialRepo.findByProviderAndProviderUserId("ldap", dn)
                .flatMap(b -> userRepo.findById(b.getUserId()))
                .orElseGet(() -> provisionLdapUser(username, dn, emailFinal, displayFinal, tenantCode));

        String access = jwt.issueAccess(user.getId(), user.getUsername(), user.getTenantCode(),
                List.of(), List.of(), "ldap");
        audit.record(user.getId(), user.getTenantCode(), "LDAP_LOGIN", "SUCCESS", user.getUsername(), ip, "dn=" + dn);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .build();
    }

    private UserEntity provisionLdapUser(String uid, String dn, String email, String displayName, String tenant) {
        UserEntity u = userRepo.save(UserEntity.builder()
                .username("ldap_" + uid)
                .passwordHash("DISABLED")
                .email(email)
                .tenantCode(tenant == null ? "default" : tenant)
                .status(1).mfaEnabled(false).failCount(0)
                .build());
        socialRepo.save(SocialBindingEntity.builder()
                .userId(u.getId())
                .provider("ldap")
                .providerUserId(dn)
                .providerUsername(displayName)
                .build());
        return u;
    }

    private java.util.Map<String,String> mapAttrs(Attributes a) throws javax.naming.NamingException {
        java.util.Map<String,String> m = new java.util.HashMap<>();
        if (a.get("mail") != null) m.put("mail", a.get("mail").get().toString());
        if (a.get("cn") != null) m.put("cn", a.get("cn").get().toString());
        return m;
    }
}
