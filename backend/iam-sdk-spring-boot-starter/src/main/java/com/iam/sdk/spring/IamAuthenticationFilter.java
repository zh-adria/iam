package com.iam.sdk.spring;

import com.iam.sdk.IamJwtVerifier;
import com.iam.sdk.IamPrincipal;
import com.iam.sdk.IamSdkException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IamAuthenticationFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = "iam.principal";

    private final IamJwtVerifier verifier;
    private final IamSdkProperties properties;

    public IamAuthenticationFilter(IamJwtVerifier verifier, IamSdkProperties properties) {
        this.verifier = verifier;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveBearer(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            IamPrincipal principal = verifier.verify(token);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities(principal)));
            filterChain.doFilter(request, response);
        } catch (IamSdkException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"Invalid IAM access token\"}");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private List<SimpleGrantedAuthority> authorities(IamPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        principal.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
        principal.getPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    }
}
