package com.iam.start.config;

import com.iam.infrastructure.security.AbacMethodSecurityExpressionHandler;
import com.iam.infrastructure.security.JwtAuthFilter;
import com.iam.infrastructure.security.ScimAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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
    private final ScimAuthFilter scimFilter;
    private final ApplicationContext applicationContext;

    @Value("${iam.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    /** SAML 成功处理器 — 如果 SamlConfig 没启用（iam.saml.enabled != true），这个 bean 也不存在。 */
    private AuthenticationSuccessHandler samlSuccessHandlerOrNull() {
        try {
            return applicationContext.getBean(AuthenticationSuccessHandler.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsSource()).and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
                // RELATIVE path（去掉 context-path /iam）。
                .antMatchers(
                    "/api/**",                            // 所有 /iam/api/* 端点免登
                    "/actuator/health",
                    "/actuator/info",
                    "/error",
                    "/oauth/**",                          // OIDC/OAuth2 都不需要登录即可调
                    "/login/**",
                    "/saml2/**",
                    "/saml/metadata/**",
                    "/cas/**"
                ).permitAll()
                .antMatchers("/oauth/authorize").authenticated()
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(scimFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // 只在 SamlConfig 成功装配时才启用 saml2Login filter — 避免空 registration 列表报错。
        AuthenticationSuccessHandler samlHandler = samlSuccessHandlerOrNull();
        if (samlHandler != null) {
            http.saml2Login(saml -> saml.successHandler(samlHandler));
        }
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
