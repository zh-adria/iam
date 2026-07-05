# JustAuth Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use inline execution in this session. Subagents are disabled for this side conversation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-written social OAuth provider calls with a JustAuth-backed adapter while preserving the existing IAM user binding, provisioning, JWT issuance, and frontend callback flow.

**Architecture:** Keep `AuthController` and frontend social login flow unchanged. Add a small provider registry and gateway around JustAuth so adding a future provider only requires a new mapping and dynamic-config rows, not changes to `SocialLoginService` business logic.

**Tech Stack:** Spring Boot 2.7.18, Java 17, Maven, JUnit 5, Mockito, Liquibase, JustAuth `me.zhyd.oauth:JustAuth`.

## Global Constraints

- Preserve existing endpoints: `/api/auth/social/{provider}/authorize` and `/api/auth/social/{provider}/callback`.
- Preserve existing provider ids: `wechat`, `alipay`, `qq`, `dingtalk`, `wecom`.
- Keep local user provisioning, social binding, JWT issuance, and audit recording inside the IAM application.
- Persist JustAuth social provider settings in `system_config` through Liquibase seed rows; YAML is only a fallback.
- Do not make real third-party HTTP calls in tests.
- Keep changes scoped to JustAuth integration files and related config/docs.

---

### Task 1: Provider Registry And Gateway Boundary

**Files:**
- Create: `backend/iam-common/src/main/java/com/iam/app/service/social/SocialProfile.java`
- Create: `backend/iam-common/src/main/java/com/iam/app/service/social/SocialAuthGateway.java`
- Create: `backend/iam-common/src/main/java/com/iam/app/service/social/JustAuthSocialAuthGateway.java`
- Test: `backend/iam-common/src/test/java/com/iam/app/service/social/JustAuthSocialAuthGatewayTest.java`
- Modify: `backend/iam-common/pom.xml`

**Interfaces:**
- Produces: `SocialAuthGateway.authorizeUrl(String provider)` and `SocialAuthGateway.fetchProfile(String provider, String code, String state)`.
- Produces: `SocialProfile(String provider, String providerUserId, String displayName, String email)`.
- Consumes: `DynamicConfig` values under `iam.social.*`, with Spring `Environment` as a fallback.

- [ ] **Step 1: Write failing tests**

```java
@Test
void rejectsUnknownProvider() {
    JustAuthSocialAuthGateway gateway = gatewayWith();

    AuthException ex = assertThrows(AuthException.class,
            () -> gateway.authorizeUrl("github"));

    assertEquals("UNKNOWN_PROVIDER", ex.getCode());
}

@Test
void rejectsUnconfiguredKnownProvider() {
    JustAuthSocialAuthGateway gateway = gatewayWith();

    AuthException ex = assertThrows(AuthException.class,
            () -> gateway.authorizeUrl("qq"));

    assertEquals("SOCIAL_NOT_CONFIGURED", ex.getCode());
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -pl iam-common -Dtest=JustAuthSocialAuthGatewayTest test`

Expected: FAIL because `JustAuthSocialAuthGateway` does not exist.

- [ ] **Step 3: Implement minimal gateway**

Create the interface, profile DTO, and gateway class with provider validation, configuration checks, and JustAuth request construction.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `mvn -pl iam-common -Dtest=JustAuthSocialAuthGatewayTest test`

Expected: PASS.

### Task 2: SocialLoginService Uses Gateway

**Files:**
- Modify: `backend/iam-common/src/main/java/com/iam/app/service/SocialLoginService.java`
- Test: `backend/iam-common/src/test/java/com/iam/app/service/SocialLoginServiceTest.java`

**Interfaces:**
- Consumes: `SocialAuthGateway.authorizeUrl(provider)` and `fetchProfile(provider, code, state)`.
- Preserves: `SocialLoginService.authorizeUrl(String provider)` and `callback(String provider, String code, String ip)`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void authorizeUrlDelegatesToGateway() {
    when(gateway.authorizeUrl("qq")).thenReturn("https://graph.qq.com/oauth2.0/authorize");

    assertEquals("https://graph.qq.com/oauth2.0/authorize", service.authorizeUrl("qq"));
}

@Test
void callbackProvisionsUserAndIssuesJwtForNewSocialProfile() {
    when(gateway.fetchProfile("qq", "code-1", null))
            .thenReturn(new SocialProfile("qq", "openid-1", "QQ User", "qq@example.com"));
    when(socialRepo.findByProviderAndProviderUserId("qq", "openid-1")).thenReturn(Optional.empty());
    when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> {
        UserEntity user = inv.getArgument(0);
        user.setId(42L);
        return user;
    });
    when(jwt.issueAccess(eq(42L), startsWith("qq_"), eq("default"), anyList(), anyList(), eq("qq")))
            .thenReturn("access-token");
    when(jwt.accessTtlSec()).thenReturn(1800L);

    TokenResponse response = service.callback("qq", "code-1", "127.0.0.1");

    assertEquals("access-token", response.getAccessToken());
    verify(socialRepo).save(argThat(binding ->
            binding.getProvider().equals("qq")
                    && binding.getProviderUserId().equals("openid-1")
                    && binding.getProviderUsername().equals("QQ User")));
    verify(audit).record(eq(42L), eq("default"), eq("SOCIAL_LOGIN"), eq("SUCCESS"),
            startsWith("qq_"), eq("127.0.0.1"), contains("provider=qq openid=openid-1"));
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -pl iam-common -Dtest=SocialLoginServiceTest test`

Expected: FAIL because `SocialLoginService` does not accept a `SocialAuthGateway`.

- [ ] **Step 3: Replace hand-written provider HTTP logic**

Inject `SocialAuthGateway`, delegate authorization/profile fetching, and leave provisioning/JWT/audit code intact.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `mvn -pl iam-common -Dtest=SocialLoginServiceTest test`

Expected: PASS.

### Task 3: Dynamic Configuration And Integration Verification

**Files:**
- Modify: `backend/iam-common/src/main/resources/db/changelog/master.xml`
- Modify: `backend/iam-common/src/main/java/com/iam/infrastructure/config/DynamicConfig.java`
- Modify: `backend/iam-common/src/main/java/com/iam/app/service/AdminAppService.java`
- Test: `backend/iam-common/src/test/java/com/iam/infrastructure/config/DynamicConfigTest.java`
- Test: `backend/iam-common/src/test/java/com/iam/app/service/AdminAppServiceTest.java`
- Test: existing backend tests

**Interfaces:**
- Creates: `system_config` with `cfg_key`, `cfg_value`, `cfg_type`, `description`, `updated_at`.
- Seeds: `iam.social.redirect-base-url` and current provider credential keys.
- Preserves: `/admin/api/config`, now returning dynamic config rows through `AdminAppService.systemConfig()`.

- [x] **Step 1: Add config table and social config seed rows**

Add Liquibase change sets for `system_config` and seed the JustAuth social-provider keys.

- [x] **Step 2: Make DynamicConfig resilient when Redis is unavailable**

Load DB config at startup. Start Redis subscription as a daemon best-effort listener and log a warning if Redis is down.

- [x] **Step 3: Return dynamic config through admin service**

`AdminAppService.systemConfig()` returns `items = dynamicConfig.listAll()`.

- [x] **Step 4: Run targeted tests**

Run: `mvn -pl iam-common test`

Expected: PASS.

- [x] **Step 5: Run backend tests**

Run: `mvn test`

Expected: PASS.
