package com.iam.infrastructure.security;

import com.iam.infrastructure.entity.ApiKeyEntity;
import com.iam.infrastructure.repository.ApiKeyRepository;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String apiKey = req.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
                String hash = sha256(apiKey);
                ApiKeyEntity key = apiKeyRepo.findByKeyHash(hash).orElse(null);
                if (key != null && key.getEnabled() && key.getExpiresAt().isAfter(Instant.now())) {
                    var principal = new org.springframework.security.core.userdetails.User(
                            "apikey:" + key.getId(), "", Collections.singletonList(
                                    new SimpleGrantedAuthority("ROLE_API_KEY")));
                    var sat = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(sat);
                    log.debug("authenticated api key prefix={}", key.getPrefix());
                }
            }
        } catch (Exception e) {
            log.debug("invalid api key: {}", e.getMessage());
        } finally {
            chain.doFilter(req, res);
        }
    }

    private static String sha256(String s) {
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
