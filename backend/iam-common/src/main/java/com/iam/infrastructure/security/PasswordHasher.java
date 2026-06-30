package com.iam.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher implements PasswordEncoder {

    private final BCryptPasswordEncoder encoder;

    public PasswordHasher(@org.springframework.beans.factory.annotation.Value("${iam.password.bcrypt-strength:10}") int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override public String encode(CharSequence raw) { return encoder.encode(raw); }
    @Override public boolean matches(CharSequence raw, String hash) { return encoder.matches(raw, hash); }
}
