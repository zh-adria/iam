package com.iam.start.config;

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
    private final com.iam.infrastructure.security.KerberosSpnegoFilter kerberosFilter;
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
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/mfa/verify",
                    "/api/auth/ldap",
                    "/api/auth/sms/**",
                    "/api/auth/magic/**",
                    "/api/auth/social/**",
                    "/api/auth/cas/**",
                    "/api/users/register",
                    "/actuator/health",
                    "/oauth/token",
                    "/oauth/.well-known/**",
                    "/oauth/jwks",
                    "/oauth/userinfo",
                    "/oauth/introspect",
                    "/oauth/revoke",
                    "/login/oauth2/**",
                    "/login/saml2/**",
                    "/saml2/**",
                    "/saml/metadata/**",
                    "/cas/**",
                    "/scim/**",
                    "/api/auth/kerberos"
                ).permitAll()
                .antMatchers("/oauth/authorize").authenticated()
                .anyRequest().authenticated()
            .and()
            .saml2Login(saml -> saml.successHandler(samlSuccessHandler))
            .addFilterBefore(kerberosFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
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
