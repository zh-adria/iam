package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.SocialBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialBindingRepository extends JpaRepository<SocialBindingEntity, Long> {
    Optional<SocialBindingEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<SocialBindingEntity> findByUserId(Long userId);
}
