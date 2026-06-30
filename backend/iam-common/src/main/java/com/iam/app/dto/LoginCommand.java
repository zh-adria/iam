package com.iam.app.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class LoginCommand {
    private String username;
    private String password;
    private String phone;
    private String smsCode;
    private String email;
    private String emailCode;
    private String tenantCode;
    private String clientId;
    private String grantType; // password, sms, email, social
    private String ip;
    private String userAgent;
}
