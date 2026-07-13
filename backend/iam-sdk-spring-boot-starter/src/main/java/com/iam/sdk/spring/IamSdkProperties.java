package com.iam.sdk.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "iam.sdk")
public class IamSdkProperties {
    private boolean enabled = true;
    private String issuer = "iam-platform";
    private String authServerBaseUrl = "http://localhost:8080/iam";
    private String jwksUri = "http://localhost:8080/iam/oauth/jwks";
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/health"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAuthServerBaseUrl() {
        return authServerBaseUrl;
    }

    public void setAuthServerBaseUrl(String authServerBaseUrl) {
        this.authServerBaseUrl = authServerBaseUrl;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : excludePaths;
    }
}
