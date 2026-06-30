package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    AuditLogEntity findTopByOrderByIdDesc();
    List<AuditLogEntity> findByUserIdOrderByOccurredAtDesc(Long userId);
}
