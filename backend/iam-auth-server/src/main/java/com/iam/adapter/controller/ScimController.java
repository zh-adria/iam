package com.iam.adapter.controller;

import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SCIM 2.0 core endpoints (RFC 7643/7644).
 * Mounted at /scim/v2 under the auth-server context-path (/iam).
 *
 * Auth: either ROLE_ADMIN (admin JWT) or ROLE_SCIM_PROVISIONER (SCIM Bearer token).
 * Configure the SCIM token via iam.scim.auth-token in application.yml.
 */
@RestController
@RequestMapping("/scim/v2")
@PreAuthorize("hasAnyRole('ADMIN', 'SCIM_PROVISIONER')")
@RequiredArgsConstructor
public class ScimController {

    private final UserRepository userRepo;

    // ---------- Users ----------

    @GetMapping("/Users")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int startIndex,
                                         @RequestParam(defaultValue = "20") int count,
                                         HttpServletRequest req) {
        List<UserEntity> all = userRepo.findAll();
        int total = all.size();
        int start = Math.max(1, startIndex);
        int end = Math.min(total, start + count - 1);
        List<UserEntity> page = start <= total ? all.subList(start - 1, end) : Collections.emptyList();

        List<Map<String, Object>> resources = page.stream()
                .map(this::toScimUser)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.put("totalResults", total);
        result.put("startIndex", start);
        result.put("itemsPerPage", end - start + 1);
        result.put("Resources", resources);
        return result;
    }

    @GetMapping("/Users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long id) {
        Optional<UserEntity> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return scimError(HttpStatus.NOT_FOUND, "noSuchObject", "User " + id + " not found");
        }
        return ResponseEntity.ok(toScimUser(opt.get()));
    }

    @PostMapping(value = "/Users", produces = "application/scim+json",
            consumes = "application/scim+json")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest req) {
        String username = (String) body.get("userName");
        String email = (String) body.get("emails");
        String phone = (String) body.get("phoneNumbers");

        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setPasswordHash("");          // ponytail: SCIM provisioning sets no password; provisioning must call reset separately.
        u.setEmail(email);
        u.setPhone(phone);
        u.setTenantCode("default");
        u.setStatus(1);
        UserEntity saved = userRepo.save(u);

        URI location = URI.create("/scim/v2/Users/" + saved.getId());
        return ResponseEntity.created(location)
                .body(toScimUser(saved));
    }

    @DeleteMapping("/Users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepo.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------- helpers ----------

    private Map<String, Object> toScimUser(UserEntity u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        m.put("id", String.valueOf(u.getId()));
        m.put("userName", u.getUsername());
        m.put("displayName", u.getUsername());
        if (u.getEmail() != null) {
            m.put("emails", List.of(Map.of("value", u.getEmail(), "type", "work")));
        }
        if (u.getPhone() != null) {
            m.put("phoneNumbers", List.of(Map.of("value", u.getPhone(), "type", "work")));
        }
        m.put("active", u.getStatus() == 1);
        m.put("tenantCode", u.getTenantCode());
        return m;
    }

    private static ResponseEntity<Map<String, Object>> scimError(HttpStatus status,
                                                                  String detail,
                                                                  String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:Error"));
        body.put("status", String.valueOf(status.value()));
        body.put("detail", detail);
        body.put("description", description);
        return new ResponseEntity<>(body, status);
    }
}
