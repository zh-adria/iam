package com.iam.infrastructure.security;

import com.iam.app.service.ScimProvisionerTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * SCIM 2.0 Bearer token authentication filter.
 *
 * Checks DB-backed provisioner tokens first; falls back to legacy shared secret
 * ({@code iam.scim.auth-token}) only when no DB tokens exist.
 */
@Slf4j
@Component
public class ScimAuthFilter extends OncePerRequestFilter {

    private final ScimProvisionerTokenService scimTokenService;
    private final String legacyToken;

    public ScimAuthFilter(ScimProvisionerTokenService scimTokenService,
                          Environment env) {
        this.scimTokenService = scimTokenService;
        this.legacyToken = env.getProperty("iam.scim.auth-token", "");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            boolean ok = scimTokenService.validate(token);
            if (!ok && legacyToken != null && !legacyToken.isEmpty() && legacyToken.equals(token)) {
                ok = true;
            }
            if (ok) {
                var principal = new org.springframework.security.core.userdetails.User(
                        "scim-provisioner", "", Collections.singletonList(
                                new SimpleGrantedAuthority("ROLE_SCIM_PROVISIONER")));
                var sat = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                sat.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(sat);
                log.debug("authenticated SCIM provisioner");
            }
        }
        chain.doFilter(req, res);
    }
}
