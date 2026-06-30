package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {
    List<UserRoleEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
