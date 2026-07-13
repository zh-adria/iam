package com.iam.sdk.spring;

import com.iam.sdk.IamAuthorizer;
import com.iam.sdk.IamPrincipal;
import com.iam.sdk.IamSdkException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class IamSecurity {
    public Optional<IamPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof IamPrincipal)) {
            return Optional.empty();
        }
        return Optional.of((IamPrincipal) authentication.getPrincipal());
    }

    public IamPrincipal requireCurrent() {
        return current().orElseThrow(() -> new IamSdkException("IAM principal is required"));
    }

    public boolean hasRole(String role) {
        return current().map(principal -> IamAuthorizer.hasRole(principal, role)).orElse(false);
    }

    public boolean hasPermission(String permission) {
        return current().map(principal -> IamAuthorizer.hasPermission(principal, permission)).orElse(false);
    }

    public void requireRole(String role) {
        IamAuthorizer.requireRole(requireCurrent(), role);
    }

    public void requirePermission(String permission) {
        IamAuthorizer.requirePermission(requireCurrent(), permission);
    }
}
