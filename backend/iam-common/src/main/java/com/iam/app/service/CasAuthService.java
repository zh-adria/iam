package com.iam.app.service;

import com.iam.app.dto.TokenResponse;
import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.AuditLogService;
import com.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CAS SSO client: validate service ticket against CAS server's /serviceValidate (CAS 2.0 protocol).
 * On success: find-or-provision local user by CAS uid, issue JWT.
 * ponytail: CAS 3.0 attributes parsing omitted — add when needing eduPerson attributes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasAuthService {

    private final UserRepository userRepo;
    private final JwtTokenService jwt;
    private final AuditLogService audit;
    private final RestTemplate http = new RestTemplate();

    @Value("${iam.cas.server-url:}") private String casServerUrl;
    @Value("${iam.cas.service-url:http://localhost:8080/iam/api/auth/cas/callback}") private String serviceUrl;

    public String authorizeUrl() {
        if (casServerUrl == null || casServerUrl.isEmpty())
            throw new AuthException("CAS_NOT_CONFIGURED", "iam.cas.server-url 未配置");
        return casServerUrl + "/login?service=" + enc(serviceUrl);
    }

    @Transactional
    public TokenResponse callback(String ticket, String ip) {
        if (casServerUrl == null || casServerUrl.isEmpty())
            throw new AuthException("CAS_NOT_CONFIGURED", "CAS 未配置");
        String validateUrl = casServerUrl + "/serviceValidate?ticket=" + enc(ticket) + "&service=" + enc(serviceUrl);
        String body;
        try {
            body = http.getForObject(validateUrl, String.class);
        } catch (Exception e) {
            throw new AuthException("CAS_ERROR", "CAS 校验失败: " + e.getMessage());
        }
        if (body == null || !body.contains("<cas:authenticationSuccess>"))
            throw new AuthException("CAS_FAIL", "ticket 无效");
        String uid = extract(body, "cas:user");
        if (uid == null || uid.isEmpty()) throw new AuthException("CAS_FAIL", "未取到 CAS user");

        UserEntity u = userRepo.findByUsernameAndTenantCode(uid, "default")
                .orElseGet(() -> userRepo.save(UserEntity.builder()
                        .username("cas_" + uid)
                        .passwordHash("CAS_DISABLED")
                        .tenantCode("default")
                        .status(1).mfaEnabled(false).failCount(0)
                        .build()));

        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getTenantCode(),
                Collections.emptyList(), Collections.emptyList(), "cas");
        audit.record(u.getId(), u.getTenantCode(), "CAS_LOGIN", "SUCCESS", u.getUsername(), ip, "uid=" + uid);
        return TokenResponse.builder()
                .accessToken(access).tokenType("Bearer")
                .expiresIn(jwt.accessTtlSec()).mfaRequired(false)
                .build();
    }

    private static String extract(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
