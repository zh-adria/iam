package com.iam.start.config;

import com.iam.infrastructure.security.AbacMethodSecurityExpressionHandler;
import com.iam.infrastructure.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableAsync
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final AuthenticationSuccessHandler samlSuccessHandler;

    @Value("${iam.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsSource()).and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
                // ponytail: antMatchers are RELATIVE to the context-path (/iam).
                // Do NOT prefix them with /iam — that path never reaches the
                // security matcher and would fall through to authenticated().
                .antMatchers(
                    // ---------- 鉴权入口（permitAll 才会流转到 Controller） ----------
                    // 注意：这里列的是 RELATIVE path（去掉 context-path /iam）。
                    // Spring Security 5.7 的 antMatchers 必须按 MATCH 顺序命中 — 任何路径
                    // 不在这个列表里，没带 token 的请求会被 SAML2LoginConfigurer 的
                    // redirect 拦截，302 到 /saml2/authenticate/...，所以列宽一点更安全。
                    "/api/**",                            // 所有 /iam/api/* 端点免登
                    "/actuator/health",
                    "/actuator/info",
                    "/error",
                    // ---------- OAuth2 / OIDC ----------
                    "/oauth/**",
                    // ---------- 所有登录页面端点（避免 SAML / OAuth2 登录 filter 未匹配时 302） ----------
                    "/login/**",
                    "/saml2/**",
                    "/saml/metadata/**",
                    "/cas/**"
                ).permitAll()
                .antMatchers("/oauth/authorize").authenticated()
                .anyRequest().authenticated()
            .and()
            .saml2Login(saml -> saml.successHandler(samlSuccessHandler))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /** Expose our custom MethodSecurityExpressionHandler so @PreAuthorize("hasPermission()") resolves SpEL. */
    @Bean
    public org.springframework.security.access.expression.method.MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            AbacMethodSecurityExpressionHandler abacExpressionHandler) {
        return abacExpressionHandler;
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.asList(allowedOrigins));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
