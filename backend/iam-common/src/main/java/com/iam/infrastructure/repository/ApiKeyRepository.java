package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {
    Optional<ApiKeyEntity> findByPrefix(String prefix);
    Optional<ApiKeyEntity> findByKeyHash(String keyHash);
    boolean existsByPrefix(String prefix);
    boolean existsByKeyHash(String keyHash);
}
