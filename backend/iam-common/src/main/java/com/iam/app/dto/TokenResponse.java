package com.iam.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String idToken; // OIDC id_token
    private String tokenType;
    private long expiresIn;
    private boolean mfaRequired;
    private String mfaToken; // short-lived token to continue login after MFA
    private List<String> roles;
    private List<String> permissions;
    private List<String> scopes;
}
