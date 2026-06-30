package com.iam.infrastructure.security;

import com.iam.infrastructure.entity.AuditLogEntity;
import com.iam.infrastructure.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repo;

    @Async
    @Transactional
    public void record(Long userId, String tenant, String action, String result,
                       String principal, String ip, String detail) {
        AuditLogEntity prev = repo.findTopByOrderByIdDesc();
        String prevHash = prev == null ? "GENESIS" : sha(prev.getId() + ":" + prev.getHashChainPrev());
        repo.save(AuditLogEntity.builder()
                .userId(userId).tenantCode(tenant == null ? "default" : tenant)
                .action(action).result(result).principal(principal).ip(ip).detail(detail)
                .hashChainPrev(prevHash).build());
    }

    private String sha(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
