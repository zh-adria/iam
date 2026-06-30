package com.iam.app.service;

import com.iam.domain.AuthException;
import com.iam.infrastructure.entity.OAuth2ClientEntity;
import com.iam.infrastructure.repository.OAuth2ClientRepository;
import com.iam.infrastructure.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2ClientAppService {

    private final OAuth2ClientRepository repo;
    private final PasswordHasher hasher;

    @Transactional
    public String registerClient(String clientId, String rawSecret, String grantTypes, String redirectUris, String scopes) {
        if (clientId == null || clientId.isEmpty()) clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        if (repo.existsById(clientId)) throw new AuthException("DUP_CLIENT", "客户端已存在");
        repo.save(OAuth2ClientEntity.builder()
                .clientId(clientId)
                .clientSecretHash(hasher.encode(rawSecret))
                .grantTypes(grantTypes)
                .redirectUris(redirectUris)
                .scopes(scopes).build());
        return clientId;
    }

    public boolean validate(String clientId, String rawSecret, String grantType, String redirectUri) {
        return repo.findById(clientId).map(c -> {
            if (!hasher.matches(rawSecret, c.getClientSecretHash())) return false;
            Set<String> grants = new HashSet<>(Arrays.asList(c.getGrantTypes().split(",")));
            if (!grants.contains(grantType)) return false;
            if (redirectUri != null && c.getRedirectUris() != null) {
                return Arrays.asList(c.getRedirectUris().split(",")).contains(redirectUri);
            }
            return true;
        }).orElse(false);
    }
}
