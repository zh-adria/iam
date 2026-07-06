package com.iam.adapter.controller;

import com.iam.infrastructure.entity.ScimGroupEntity;
import com.iam.infrastructure.entity.ScimGroupMemberEntity;
import com.iam.infrastructure.entity.UserEntity;
import com.iam.infrastructure.repository.ScimGroupMemberRepository;
import com.iam.infrastructure.repository.ScimGroupRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ScimGroupRepository groupRepo;
    private final ScimGroupMemberRepository memberRepo;
    private static final String DEFAULT_TENANT = "default";

    // ---------- ServiceProviderConfig ----------

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("supported", true);
        m.put("patch", patch);
        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("supported", true);
        bulk.put("maxOperations", 1000);
        bulk.put("maxPayloadSize", 1048576);
        m.put("bulk", bulk);
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("supported", true);
        filter.put("maxResults", 200);
        m.put("filter", filter);
        Map<String, Object> changePwd = new LinkedHashMap<>();
        changePwd.put("supported", false);
        m.put("changePassword", changePwd);
        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("supported", true);
        m.put("sort", sort);
        Map<String, Object> etag = new LinkedHashMap<>();
        etag.put("supported", false);
        m.put("etag", etag);
        return m;
    }

    // ---------- Users ----------

    @GetMapping("/Users")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int startIndex,
                                         @RequestParam(defaultValue = "20") int count,
                                         @RequestParam(required = false) String filter,
                                         HttpServletRequest req) {
        List<UserEntity> all = userRepo.findAll();
        if (filter != null && !filter.isBlank()) {
            all = applyUserFilter(all, filter);
        }
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

    // ---------- User PATCH (RFC 7644) ----------

    @PatchMapping(value = "/Users/{id}", produces = "application/scim+json",
            consumes = "application/scim+json")
    public ResponseEntity<Map<String, Object>> patchUser(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        UserEntity u = userRepo.findById(id).orElse(null);
        if (u == null) {
            return scimError(HttpStatus.NOT_FOUND, "noSuchObject", "User " + id + " not found");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ops = (List<Map<String, Object>>) body.get("Operations");
        if (ops == null) {
            return scimError(HttpStatus.BAD_REQUEST, "invalidSyntax", "Missing Operations");
        }
        for (Map<String, Object> op : ops) {
            String opType = (String) op.get("op");
            String path = (String) op.get("path");
            Object value = op.get("value");
            if (!"add".equals(opType) && !"replace".equals(opType)) continue;
            if ("userName".equals(path) && value instanceof String) {
                u.setUsername((String) value);
            } else if ("emails".equals(path) && value instanceof List && !((List<?>) value).isEmpty()) {
                Object first = ((List<?>) value).get(0);
                if (first instanceof Map) {
                    Object emailVal = ((Map<?, ?>) first).get("value");
                    if (emailVal instanceof String) u.setEmail((String) emailVal);
                }
            } else if ("phoneNumbers".equals(path) && value instanceof List && !((List<?>) value).isEmpty()) {
                Object first = ((List<?>) value).get(0);
                if (first instanceof Map) {
                    Object phoneVal = ((Map<?, ?>) first).get("value");
                    if (phoneVal instanceof String) u.setPhone((String) phoneVal);
                }
            } else if ("active".equals(path) && value instanceof Boolean) {
                u.setStatus(((Boolean) value) ? 1 : 0);
            }
        }
        userRepo.save(u);
        return ResponseEntity.ok(toScimUser(u));
    }

    // ---------- Schemas ----------

    @GetMapping("/Schemas")
    public Map<String, Object> schemas() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        m.put("totalResults", 3);
        m.put("itemsPerPage", 3);
        m.put("startIndex", 1);
        m.put("Resources", List.of(
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Schema")),
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Schema")),
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Schema"))
        ));
        return m;
    }

    // ---------- ResourceTypes ----------

    @GetMapping("/ResourceTypes")
    public Map<String, Object> resourceTypes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        m.put("totalResults", 3);
        m.put("itemsPerPage", 3);
        m.put("startIndex", 1);
        m.put("Resources", List.of(
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                        "id", "User", "name", "User",
                        "endpoint", "/Users",
                        "schema", "urn:ietf:params:scim:schemas:core:2.0:User"),
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                        "id", "Group", "name", "Group",
                        "endpoint", "/Groups",
                        "schema", "urn:ietf:params:scim:schemas:core:2.0:Group"),
                Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                        "id", "ServiceProviderConfig", "name", "ServiceProviderConfig",
                        "endpoint", "/ServiceProviderConfig",
                        "schema", "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig")
        ));
        return m;
    }

    // ---------- Groups ----------

    @GetMapping("/Groups")
    public Map<String, Object> listGroups(@RequestParam(defaultValue = "1") int startIndex,
                                          @RequestParam(defaultValue = "20") int count,
                                          @RequestParam(required = false) String filter,
                                          HttpServletRequest req) {
        List<ScimGroupEntity> all = groupRepo.findByTenantCode(DEFAULT_TENANT);
        if (filter != null && !filter.isEmpty()) {
            String fl = filter.toLowerCase().trim();
            if (fl.startsWith("displayname eq ")) {
                String val = fl.substring("displayname eq ".length()).replace("\"", "").trim();
                all = all.stream().filter(g -> val.equals(g.getDisplayName()))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                all = all.stream().filter(g -> g.getDisplayName().toLowerCase().contains(fl)
                        || (g.getExternalId() != null && g.getExternalId().toLowerCase().contains(fl)))
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        int total = all.size();
        int start = Math.max(1, startIndex);
        int end = Math.min(total, start + count - 1);
        List<ScimGroupEntity> page = start <= total ? all.subList(start - 1, end) : java.util.Collections.emptyList();

        List<Map<String, Object>> resources = page.stream()
                .map(this::toScimGroup)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        result.put("totalResults", total);
        result.put("startIndex", start);
        result.put("itemsPerPage", end - start + 1);
        result.put("Resources", resources);
        return result;
    }

    @GetMapping("/Groups/{id}")
    public ResponseEntity<Map<String, Object>> getGroup(@PathVariable Long id) {
        ScimGroupEntity g = groupRepo.findById(id).orElse(null);
        if (g == null) {
            return scimError(HttpStatus.NOT_FOUND, "noSuchObject", "Group " + id + " not found");
        }
        return ResponseEntity.ok(toScimGroup(g));
    }

    @PostMapping(value = "/Groups", produces = "application/scim+json",
            consumes = "application/scim+json")
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody Map<String, Object> body,
                                                           HttpServletRequest req) {
        String displayName = (String) body.get("displayName");
        String externalId = (String) body.get("externalId");

        ScimGroupEntity g = ScimGroupEntity.builder()
                .displayName(displayName)
                .externalId(externalId)
                .members("[]")
                .tenantCode(DEFAULT_TENANT)
                .build();
        ScimGroupEntity saved = groupRepo.save(g);

        // Persist members as individual rows
        if (body.get("members") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
            for (Map<String, Object> m : members) {
                ScimGroupMemberEntity me = ScimGroupMemberEntity.builder()
                        .groupId(saved.getId())
                        .memberType(m.get("type") != null ? (String) m.get("type") : "User")
                        .memberValue(String.valueOf(m.get("value")))
                        .displayName((String) m.get("display"))
                        .tenantCode(DEFAULT_TENANT)
                        .build();
                memberRepo.save(me);
            }
        }

        URI location = URI.create("/scim/v2/Groups/" + saved.getId());
        return ResponseEntity.created(location).body(toScimGroup(saved));
    }

    @PatchMapping(value = "/Groups/{id}", produces = "application/scim+json",
            consumes = "application/scim+json")
    public ResponseEntity<Map<String, Object>> patchGroup(@PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        ScimGroupEntity g = groupRepo.findById(id).orElse(null);
        if (g == null) {
            return scimError(HttpStatus.NOT_FOUND, "noSuchObject", "Group " + id + " not found");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
        if (operations == null) {
            return scimError(HttpStatus.BAD_REQUEST, "invalidSyntax", "Missing Operations");
        }
        for (Map<String, Object> op : operations) {
            String opType = (String) op.get("op");
            String path = (String) op.get("path");
            Object value = op.get("value");
            if ("add".equals(opType) || "replace".equals(opType)) {
                if ("displayName".equals(path) && value instanceof String) {
                    g.setDisplayName((String) value);
                } else if ("members".equals(path) && value instanceof List) {
                    // Sync members: delete old, insert new
                    memberRepo.deleteByGroupId(id);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> members = (List<Map<String, Object>>) value;
                    for (Map<String, Object> m : members) {
                        ScimGroupMemberEntity me = ScimGroupMemberEntity.builder()
                                .groupId(id)
                                .memberType(m.get("type") != null ? (String) m.get("type") : "User")
                                .memberValue(String.valueOf(m.get("value")))
                                .displayName((String) m.get("display"))
                                .tenantCode(DEFAULT_TENANT)
                                .build();
                        memberRepo.save(me);
                    }
                }
            } else if ("remove".equals(opType) && "members".equals(path)) {
                memberRepo.deleteByGroupId(id);
            }
        }
        groupRepo.save(g);
        return ResponseEntity.ok(toScimGroup(g));
    }

    @DeleteMapping("/Groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        if (!groupRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        memberRepo.deleteByGroupId(id);
        groupRepo.deleteById(id);
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

    private Map<String, Object> toScimGroup(ScimGroupEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));
        m.put("id", String.valueOf(g.getId()));
        m.put("displayName", g.getDisplayName());
        if (g.getExternalId() != null) m.put("externalId", g.getExternalId());
        m.put("tenantCode", g.getTenantCode());
        // Read members from entity relationship table
        List<ScimGroupMemberEntity> members = memberRepo.findByGroupId(g.getId());
        List<Map<String, Object>> memberMaps = members.stream().map(me -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("value", me.getMemberValue());
            if (me.getDisplayName() != null) mm.put("display", me.getDisplayName());
            return mm;
        }).collect(Collectors.toList());
        m.put("members", memberMaps);
        return m;
    }

    private static List<UserEntity> applyUserFilter(List<UserEntity> all, String filter) {
        String f = filter.toLowerCase().trim();
        // Minimal SCIM filter: userName eq "X" or emails pr
        if (f.startsWith("username eq ")) {
            String val = f.substring("username eq ".length()).replace("\"", "").trim();
            return all.stream().filter(u -> val.equals(u.getUsername())).collect(Collectors.toList());
        }
        if (f.startsWith("emails pr")) {
            return all.stream().filter(u -> u.getEmail() != null && !u.getEmail().isEmpty()).collect(Collectors.toList());
        }
        if (f.contains(" eq ")) {
            String[] parts = f.split(" eq ", 2);
            String attr = parts[0].trim();
            String val = parts[1].replace("\"", "").trim();
            return all.stream().filter(u -> {
                if ("username".equals(attr)) return val.equals(u.getUsername());
                if ("email".equals(attr)) return val.equals(u.getEmail());
                return false;
            }).collect(Collectors.toList());
        }
        // fallback: contains
        return all.stream().filter(u ->
                (u.getUsername() != null && u.getUsername().toLowerCase().contains(f))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(f))
        ).collect(Collectors.toList());
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
