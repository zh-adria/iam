package com.iam.infrastructure.security;

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
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims c = jwt.parse(token);
                String jti = (String) c.get("jti");
                if (jti != null && !cache.isAccessValid(jti)) {
                    log.debug("token revoked/not in cache: {}", jti);
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> perms = (List<String>) c.getOrDefault("perms", Collections.emptyList());
                    var auths = perms.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                    var principal = new IamPrincipal(c.get("uid", Long.class), c.getSubject(),
                            c.get("tenant", String.class), c.get("roles", List.class));
                    var sat = new UsernamePasswordAuthenticationToken(principal, null, auths);
                    SecurityContextHolder.getContext().setAuthentication(sat);
                }
            } catch (Exception e) {
                log.debug("invalid jwt: {}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
