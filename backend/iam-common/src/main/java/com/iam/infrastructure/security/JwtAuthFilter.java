package com.iam.infrastructure.security;

import com.iam.infrastructure.tenant.CurrentTenantHolder;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwt;
    private final TokenCacheService cache;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                Claims c = jwt.parse(token);
                String jti = (String) c.get("jti");
                if (jti != null && !cache.isAccessValid(jti)) {
                    log.debug("token revoked/not in cache: {}", jti);
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> perms = (List<String>) c.getOrDefault("perms", Collections.emptyList());
                    List<String> roles = (List<String>) c.getOrDefault("roles", Collections.emptyList());
                    // Both roles and perms become Spring Security authorities so
                    // @PreAuthorize("hasRole('ADMIN')") and permission checks work.
                    // Roles are stored without ROLE_ prefix in the DB but with the
                    // prefix in the JWT claim, so Spring's default VoteRolePrefix
                    // matches direct authority names like "ROLE_ADMIN" here.
                    List<SimpleGrantedAuthority> auths = new ArrayList<>();
                    for (String r : roles) auths.add(new SimpleGrantedAuthority(r));
                    for (String p : perms) auths.add(new SimpleGrantedAuthority(p));
                    var principal = new IamPrincipal(c.get("uid", Long.class), c.getSubject(),
                            c.get("tenant", String.class), c.get("roles", List.class));
                    var sat = new UsernamePasswordAuthenticationToken(principal, null, auths);
                    SecurityContextHolder.getContext().setAuthentication(sat);
                    CurrentTenantHolder.set(c.get("tenant", String.class));
                }
            }
            chain.doFilter(req, res);
        } catch (Exception e) {
            log.debug("invalid jwt: {}", e.getMessage());
            chain.doFilter(req, res);
        } finally {
            CurrentTenantHolder.clear();
        }
    }
}
