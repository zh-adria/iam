package com.iam.app.service;

import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.UserRepository;
import com.iam.infrastructure.security.TokenCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Extracted failure recorder with REQUIRES_NEW so the lockout survives the
 * surrounding login transaction's rollback on AuthException.
 * ponytail: self-invocation of @Transactional from within the same bean is a no-op, hence the separate bean.
 */
@Service
@RequiredArgsConstructor
public class LoginFailureRecorder {

    private final UserRepository userRepo;
    private final TokenCacheService cache;

    @Value("${iam.password.max-fail-count:5}")
    private int maxFail;
    @Value("${iam.password.lock-minutes:30}")
    private int lockMin;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(UserEntity user) {
        int n = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
        user.setFailCount(n);
        if (n >= maxFail) {
            user.setStatus(2);
            user.setLockedUntil(Instant.now().plusSeconds(lockMin * 60L));
            cache.lockUser(user.getId(), lockMin);
        }
        userRepo.save(user);
    }
}
