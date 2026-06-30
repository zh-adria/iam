package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.OAuth2ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuth2ClientRepository extends JpaRepository<OAuth2ClientEntity, String> {
}
