package com.iam.app.service.social;

public class SocialProfile {
    private final String provider;
    private final String providerUserId;
    private final String displayName;
    private final String email;

    public SocialProfile(String provider, String providerUserId, String displayName, String email) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.displayName = displayName;
        this.email = email;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }
}
