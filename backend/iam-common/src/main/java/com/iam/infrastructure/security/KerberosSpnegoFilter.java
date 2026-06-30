package com.iam.infrastructure.security;

import com.iam.app.dto.TokenResponse;
import com.iam.app.service.AuthAppService;
import com.iam.app.dto.LoginCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Kerberos / SPNEGO filter — emits WWW-Authenticate: Negotiate on 401, accepts Authorization: Negotiate <spnego token>.
 * ponytail: stub — real impl needs org.ietf.jgss + JAAS krb5 login config. Currently echoes 401 with challenge.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KerberosSpnegoFilter extends OncePerRequestFilter {

    private final AuthAppService auth;

    @Value("${iam.kerberos.enabled:false}") private boolean enabled;
    @Value("${iam.kerberos.realm:}") private String realm;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (!path.endsWith("/api/auth/kerberos")) { chain.doFilter(req, res); return; }

        String authz = req.getHeader("Authorization");
        if (authz == null || !authz.startsWith("Negotiate ")) {
            res.setHeader("WWW-Authenticate", "Negotiate" + (realm.isEmpty() ? "" : " realm=\"" + realm + "\""));
            res.setStatus(401);
            res.getWriter().write("{\"code\":\"KERBEROS_CHALLENGE\",\"message\":\"Negotiate required\"}");
            return;
        }
        if (!enabled) {
            res.setStatus(501);
            res.getWriter().write("{\"code\":\"KERBEROS_DISABLED\",\"message\":\"iam.kerberos.enabled=false\"}");
            return;
        }
        // ponytail: real impl decodes SPNEGO token via GSSContext, validates against KDC, extracts user principal.
        // For now, return 501 — wire JAAS + krb5.conf to enable.
        res.setStatus(501);
        res.getWriter().write("{\"code\":\"KERBEROS_STUB\",\"message\":\"解码 SPNEGO token 需要 org.ietf.jgss + JAAS 配置\"}");
    }
}
