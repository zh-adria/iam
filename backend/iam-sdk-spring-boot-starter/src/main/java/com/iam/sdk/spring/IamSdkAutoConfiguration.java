package com.iam.sdk.spring;

import com.iam.sdk.IamAuthClient;
import com.iam.sdk.IamJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnClass(IamJwtVerifier.class)
@EnableConfigurationProperties(IamSdkProperties.class)
@ConditionalOnProperty(prefix = "iam.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IamSdkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public IamAuthClient iamAuthClient(IamSdkProperties properties) {
        return new IamAuthClient(properties.getAuthServerBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public IamJwtVerifier iamJwtVerifier(IamSdkProperties properties) {
        return new IamJwtVerifier(properties.getIssuer(), properties.getJwksUri());
    }

    @Bean
    @ConditionalOnMissingBean
    public IamSecurity iamSecurity() {
        return new IamSecurity();
    }

    @Bean
    @ConditionalOnMissingBean
    public IamAuthenticationFilter iamAuthenticationFilter(IamJwtVerifier verifier, IamSdkProperties properties) {
        return new IamAuthenticationFilter(verifier, properties);
    }

    @Bean
    public FilterRegistrationBean<IamAuthenticationFilter> iamAuthenticationFilterRegistration(
            IamAuthenticationFilter filter) {
        FilterRegistrationBean<IamAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
