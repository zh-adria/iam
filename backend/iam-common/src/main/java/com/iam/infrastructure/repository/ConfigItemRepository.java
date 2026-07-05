package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.ConfigItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigItemRepository extends JpaRepository<ConfigItemEntity, Long> {
    Optional<ConfigItemEntity> findByKey(String key);
}
