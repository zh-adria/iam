package com.iam.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
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
 * ponytail: MVP — shared secret from {@code iam.scim.auth-token} config property.
 * External provisioning systems (Okta, JumpCloud) send:
 *   Authorization: Bearer <scim-token>
 * Production upgrade: per-IdP tokens stored in DB, rotated periodically.
 */
@Slf4j
@Component
public class ScimAuthFilter extends OncePerRequestFilter {

    private final String scimToken;

    public ScimAuthFilter(org.springframework.core.env.Environment env) {
        this.scimToken = env.getProperty("iam.scim.auth-token", "");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        if (scimToken.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (scimToken.equals(token)) {
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
