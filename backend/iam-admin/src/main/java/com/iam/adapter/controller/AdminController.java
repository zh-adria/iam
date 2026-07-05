package com.iam.adapter.controller;

import com.iam.app.dto.ApiResult;
import com.iam.app.service.AdminAppService;
import com.iam.app.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Console API — all management CRUD under /admin/api.
 * All endpoints require ROLE_ADMIN.
 * ponytail: one controller for all admin CRUD — split per-resource when this exceeds ~400 lines.
 */
@RestController
@RequestMapping("/admin/api")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAppService admin;
    private final ApiKeyService apiKeyService;

    // ---------- users ----------
    @GetMapping("/users")
    public ApiResult<Map<String, Object>> listUsers(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) String tenant) {
        return ApiResult.ok(admin.listUsers(page, size, tenant));
    }

    @PostMapping("/users")
    public ApiResult<Map<String, Object>> createUser(@RequestBody Map<String, Object> b) {
        return ApiResult.ok(admin.createUser(
                (String) b.get("username"), (String) b.get("password"),
                (String) b.get("email"), (String) b.get("phone"),
                (String) b.get("tenantCode"),
                b.get("status") == null ? 1 : ((Number) b.get("status")).intValue()));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> b) {
        admin.resetPassword(id, b.get("password"));
        return ApiResult.ok(null, "密码已重置");
    }

    @PostMapping("/users/{id}/status")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        admin.setUserStatus(id, ((Number) b.get("status")).intValue());
        return ApiResult.ok(null, "状态已更新");
    }

    @PostMapping("/users/{id}/unlock")
    public ApiResult<Void> unlock(@PathVariable Long id) {
        admin.unlockUser(id);
        return ApiResult.ok(null, "已解锁");
    }

    @DeleteMapping("/users/{id}")
    public ApiResult<Void> deleteUser(@PathVariable Long id) {
        admin.deleteUser(id);
        return ApiResult.ok(null, "已删除");
    }

    @PostMapping("/users/{id}/roles/{role}")
    public ApiResult<Void> assignRole(@PathVariable Long id, @PathVariable String role) {
        admin.assignRole(id, role);
        return ApiResult.ok(null, "已分配");
    }

    @DeleteMapping("/users/{id}/roles/{role}")
    public ApiResult<Void> revokeRole(@PathVariable Long id, @PathVariable String role) {
        admin.revokeRole(id, role);
        return ApiResult.ok(null, "已撤销");
    }

    // ---------- roles ----------
    @GetMapping("/roles")
    public ApiResult<Map<String, Object>> listRoles(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String tenant) {
        var rolesPage = admin.listRoles(page, size, tenant);
        return ApiResult.ok(toResultMap(rolesPage));
    }

    @PostMapping("/roles")
    public ApiResult<Void> createRole(@RequestBody Map<String, String> b) {
        admin.createRole(b.get("code"), b.get("name"), b.get("tenantCode"));
        return ApiResult.ok(null, "已创建");
    }

    @DeleteMapping("/roles/{code}")
    public ApiResult<Void> deleteRole(@PathVariable String code) {
        admin.deleteRole(code);
        return ApiResult.ok(null, "已删除");
    }

    @GetMapping("/roles/{role}/permissions")
    public ApiResult<Object> listRolePermissions(@PathVariable String role) {
        return ApiResult.ok(admin.listRolePermissions(role));
    }

    // ---------- permissions ----------
    @GetMapping("/permissions")
    public ApiResult<Map<String, Object>> listPermissions(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var p = admin.listPermissions(page, size);
        return ApiResult.ok(toResultMap(p));
    }

    @PostMapping("/permissions")
    public ApiResult<Void> createPermission(@RequestBody Map<String, String> b) {
        admin.createPermission(b.get("code"), b.get("type"), b.get("name"),
                b.get("resource"), b.get("action"), b.get("spel"));
        return ApiResult.ok(null, "已创建");
    }

    @DeleteMapping("/permissions/{code}")
    public ApiResult<Void> deletePermission(@PathVariable String code) {
        admin.deletePermission(code);
        return ApiResult.ok(null, "已删除");
    }

    @PostMapping("/roles/{role}/permissions/{perm}")
    public ApiResult<Void> grant(@PathVariable String role, @PathVariable String perm) {
        admin.grantPermission(role, perm);
        return ApiResult.ok(null, "已授权");
    }

    @DeleteMapping("/roles/{role}/permissions/{perm}")
    public ApiResult<Void> revoke(@PathVariable String role, @PathVariable String perm) {
        admin.revokePermission(role, perm);
        return ApiResult.ok(null, "已撤销");
    }

    // ---------- tenants ----------
    @GetMapping("/tenants")
    public ApiResult<Map<String, Object>> listTenants(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(toResultMap(admin.listTenants(page, size)));
    }

    @PostMapping("/tenants")
    public ApiResult<Void> upsertTenant(@RequestBody Map<String, Object> b) {
        admin.upsertTenant((String) b.get("code"), (String) b.get("name"),
                (String) b.get("isolationMode"), (String) b.get("ldapUrl"),
                (String) b.get("ldapBase"), (Boolean) b.get("enabled"));
        return ApiResult.ok(null, "已保存");
    }

    @DeleteMapping("/tenants/{code}")
    public ApiResult<Void> deleteTenant(@PathVariable String code) {
        admin.deleteTenant(code.trim());
        return ApiResult.ok(null, "已删除");
    }

    @DeleteMapping("/tenants")
    public ResponseEntity<ApiResult<Void>> deleteTenantWithoutCode() {
        return ResponseEntity.badRequest().body(ApiResult.fail("BAD_REQUEST", "租户编码不能为空"));
    }

    // ---------- oauth2 clients ----------
    @GetMapping("/oauth2/clients")
    public ApiResult<Map<String, Object>> listClients(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(toResultMap(admin.listClients(page, size)));
    }

    @PostMapping("/oauth2/clients")
    public ApiResult<Void> upsertClient(@RequestBody Map<String, String> b) {
        admin.upsertClient(b.get("clientId"), b.get("clientSecret"),
                b.get("grantTypes"), b.get("redirectUris"), b.get("scopes"));
        return ApiResult.ok(null, "已保存");
    }

    @DeleteMapping("/oauth2/clients/{clientId}")
    public ApiResult<Void> deleteClient(@PathVariable String clientId) {
        admin.deleteClient(clientId);
        return ApiResult.ok(null, "已删除");
    }

    // ---------- SAML IdP registrations ----------
    @GetMapping("/saml/idps")
    public ApiResult<Object> listSamlIdps(@RequestParam(required = false) String tenant) {
        return ApiResult.ok(admin.listSamlIdps(tenant));
    }

    @PostMapping("/saml/idps")
    public ApiResult<Void> upsertSamlIdp(@RequestBody Map<String, Object> b) {
        admin.upsertSamlIdp(
                (String) b.get("tenantCode"),
                (String) b.get("registrationId"),
                (String) b.get("idpEntityId"),
                (String) b.get("idpSsoUrl"),
                (String) b.get("idpMetadataUrl"),
                (String) b.get("idpMetadataXml"),
                (String) b.get("spEntityId"),
                (String) b.get("acsTemplate"),
                (Boolean) b.get("enabled"));
        return ApiResult.ok(null, "已保存");
    }

    @DeleteMapping("/saml/idps/{tenantCode}/{registrationId}")
    public ApiResult<Void> deleteSamlIdp(@PathVariable String tenantCode, @PathVariable String registrationId) {
        admin.deleteSamlIdp(tenantCode, registrationId);
        return ApiResult.ok(null, "已删除");
    }

    // ---------- audit ----------
    @GetMapping("/audit")
    public ApiResult<Map<String, Object>> listAudit(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size,
                                                     @RequestParam(required = false) Long userId) {
        return ApiResult.ok(admin.listAudit(page, size, userId));
    }

    private Map<String, Object> toResultMap(org.springframework.data.domain.Page<?> page) {
        Map<String, Object> r = new HashMap<>();
        r.put("total", page.getTotalElements());
        r.put("page", page.getNumber() + 1);
        r.put("size", page.getSize());
        r.put("rows", page.getContent());
        return r;
    }

    // ---------- config ----------
    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        return ApiResult.ok(admin.systemConfig());
    }

    @PutMapping("/config")
    public ApiResult<Void> updateConfig(@RequestBody Map<String, String> body) {
        admin.setSystemConfig(body.get("key"), body.get("value"), body.getOrDefault("type", "string"));
        return ApiResult.ok(null, "已保存");
    }

    @DeleteMapping("/config/{key}")
    public ApiResult<Void> deleteConfig(@PathVariable String key) {
        admin.deleteSystemConfig(key);
        return ApiResult.ok(null, "已删除");
    }

    // ---------- api keys ----------
    @PostMapping("/api-keys")
    public ApiResult<Map<String, Object>> createApiKey(@RequestBody Map<String, String> body) {
        String prefix = body.getOrDefault("prefix", "live");
        String name = body.get("name");
        String owner = body.get("owner");
        String scope = body.get("scope");
        long ttl = body.containsKey("ttlSeconds") ? Long.parseLong(body.get("ttlSeconds")) : 365L * 24 * 3600;
        return ApiResult.ok(apiKeyService.createKey(prefix, name, owner, "default", scope, ttl));
    }

    @GetMapping("/api-keys")
    public ApiResult<List<Map<String, Object>>> listApiKeys() {
        return ApiResult.ok(apiKeyService.listKeys());
    }

    @DeleteMapping("/api-keys/{id}")
    public ApiResult<Void> revokeApiKey(@PathVariable Long id) {
        apiKeyService.revokeKey(id);
        return ApiResult.ok(null, "已吊销");
    }
}
