package com.iam.demo;

import com.iam.sdk.IamPrincipal;
import com.iam.sdk.spring.IamSecurity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final IamSecurity iamSecurity;

    public OrderController(IamSecurity iamSecurity) {
        this.iamSecurity = iamSecurity;
    }

    @GetMapping
    public Map<String, Object> listOrders() {
        iamSecurity.requirePermission("iam:menu:dashboard");
        IamPrincipal principal = iamSecurity.requireCurrent();
        return Map.of(
                "user", principal.getUsername(),
                "tenant", principal.getTenant(),
                "orders", List.of(
                        Map.of("id", "ORD-1001", "status", "PENDING"),
                        Map.of("id", "ORD-1002", "status", "APPROVED")));
    }

    @PostMapping("/approve")
    public Map<String, Object> approveOrder() {
        iamSecurity.requirePermission("iam:role:create");
        IamPrincipal principal = iamSecurity.requireCurrent();
        return Map.of(
                "approved", true,
                "operator", principal.getUsername(),
                "requiredPermission", "iam:role:create");
    }
}
