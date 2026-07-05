package com.iam.app.service.social;

public interface SocialAuthGateway {
    String authorizeUrl(String provider);

    SocialProfile fetchProfile(String provider, String code, String state);
}
