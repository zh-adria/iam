# IAM Platform — 技术手册

> 版本：1.0.0 · 更新：2026-06-30

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│  Frontend (Vue3 + TS + Element Plus + Pinia)  :5173           │
│   └ /iam/admin/* → :8081      └ /iam/* → :8080                │
└──────────────────────────────────────────────────────────────┘
            │                              │
            ▼                              ▼
┌────────────────────────┐      ┌────────────────────────────┐
│ iam-auth-server :8080   │      │ iam-admin :8081            │
│ 运行态：登录/令牌/SSO    │      │ 管理态：CRUD + 审计         │
│ context-path=/iam       │      │ context-path=/iam           │
└────────────────────────┘      └────────────────────────────┘
            │                              │
            └──────────┬───────────────────┘
                       ▼
        ┌───────────────────────────────┐
        │  iam-common（共享 jar）        │
        │  Cola 4 层：domain / app /     │
        │  infrastructure / adapter      │
        └───────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
        MySQL 8.0              Redis 7
        (用户/角色/权限/        (SMS 码/Magic
         审计/RefreshToken)      Token/限流/缓存)
```

## 2. 模块布局

```
iam/
├── backend/
│   ├── pom.xml                    # 父 POM (packaging=pom, 3 模块)
│   ├── iam-common/                # 共享：domain + app + infrastructure
│   │   └── src/main/java/com/iam/
│   │       ├── app/dto/           # ApiResult, LoginCommand, TokenResponse
│   │       ├── app/service/       # AuthAppService, OAuth2AuthService,
│   │       │                      # SocialLoginService, SmsCodeService,
│   │       │                      # MagicLinkService, CasAuthService,
│   │       │                      # LdapAuthService, AdminAppService, ...
│   │       ├── domain/            # AuthException
│   │       ├── infrastructure/
│   │       │   ├── entity/        # 10 个 JPA 实体
│   │       │   ├── repository/    # 10 个 Spring Data repo
│   │       │   ├── security/      # JwtTokenService, JwtAuthFilter,
│   │       │   │                  # AuthCodeStore, TokenCacheService,
│   │       │   │                  # PasswordHasher, TotpService,
│   │       │   │                  # KerberosSpnegoFilter, AuditLogService,
│   │       │   │                  # IamPrincipal
│   │       │   └── ldap/          # LdapConfig
│   │       └── adapter/controller/ # GlobalExceptionHandler
│   ├── iam-auth-server/           # 运行态服务
│   │   └── src/main/java/com/iam/
│   │       ├── adapter/controller/ # AuthController, OAuth2Controller,
│   │       │                       # UserController, StubProtocolController
│   │       └── start/             # IamAuthServerApplication
│   │           └── config/        # SecurityConfig, SamlConfig
│   └── iam-admin/                 # 管理态服务
│       └── src/main/java/com/iam/
│           ├── adapter/controller/ # AdminController
│           └── start/             # IamAdminApplication
│               └── config/        # SecurityConfig（仅 ROLE_ADMIN）
└── frontend/                      # Vue3 SPA
```

## 3. Cola 四层映射

| Cola 层 | 包 | 职责 |
|---------|-----|------|
| adapter | `com.iam.adapter.controller` | HTTP 入口，参数校验，调用 app 服务 |
| app | `com.iam.app.service` / `app.dto` | 用例编排、事务边界、DTO 转换 |
| domain | `com.iam.domain` | 领域异常、领域服务（当前最小化） |
| infrastructure | `com.iam.infrastructure.*` | JPA 实体、Repository、JWT、Redis、LDAP |

> ponytail: domain 层目前仅含 `AuthException`。当业务规则复杂到需要独立于 JPA 实体时，再把领域模型从 entity 抽出到 domain。

## 4. 数据模型

10 张表（Liquibase `db/changelog/master.xml` 管理）：

| 表 | 关键字段 | 说明 |
|----|---------|------|
| `users` | id, username, password_hash, email, phone, tenant, status, mfa_secret | 0=禁用 1=启用 2=锁定 |
| `tenants` | code, name, isolation_mode, ldap_url, ldap_base, enabled | SHARED / SCHEMA_PER_TENANT |
| `roles` | code, name, tenant | 租户隔离 |
| `permissions` | code, type, name, resource, action, spel | type: API/MENU/BUTTON/DATA |
| `user_roles` | user_id, role_code | 多对多 |
| `role_permissions` | role_code, perm_code | 多对多 |
| `oauth2_clients` | client_id, secret_hash, redirect_uris, grant_types, scopes | OAuth2 注册 |
| `refresh_tokens` | jti, user_id, client_id, expires_at, revoked | 可撤销刷新令牌 |
| `social_bindings` | user_id, provider, open_id | 第三方账号绑定 |
| `audit_log` | id, principal, action, ip, occurred_at, hash_chain_prev | 哈希链防篡改 |

## 5. JWT 设计

- 算法：HS256（单实例够用；多资源服务器共享密钥即可）。
- Header：`{alg: HS256, typ: JWT, kid: "iam-hs256"}`。
- Payload：`sub`（userId）、`preferred_username`、`email`、`tid`（tenant）、`roles`（数组）、`cid`（clientId，机机令牌时）、`iat`、`exp`、`iss=iam-platform`、`jti`。
- access TTL 30 分钟，refresh TTL 7 天。
- 撤销：access 通过 `TokenCacheService.isAccessRevoked(jti)` 检查黑名单；refresh 通过 `refresh_tokens.revoked` 列。
- ID Token（OIDC）：`issueIdToken(userId, subject, clientId, nonce, email, preferredUsername, tenant)`，HS256 签名，含 `nonce` 防重放。

> 切换 RS256：在 `JwtTokenService` 注入 `KeyPair` Bean，签发用 `SignatureAlgorithm.RS256`，公开 JWKS 端点返回公钥。当前 `/.well-known/jwks` 返回 HS256 的占位 kid。

## 6. 安全配置

> ponytail: antMatchers 是相对 context-path（`/iam`）的路径，**不要带 `/iam` 前缀**，否则匹配不上、落到 `authenticated()`。

### iam-auth-server SecurityConfig

```
permitAll: /api/auth/login, /refresh, /mfa/verify, /ldap, /sms/**, /magic/**,
           /social/**, /cas/**, /users/register, /oauth/token, /.well-known/**,
           /jwks, /userinfo, /introspect, /revoke, /login/oauth2/**, /login/saml2/**,
           /saml2/**, /saml/metadata/**, /cas/**, /scim/**, /api/auth/kerberos,
           /actuator/health
authenticated: /oauth/authorize
anyRequest: authenticated
```

Filter 链：CORS → SAML2 → KerberosSpnegoFilter → JwtAuthFilter → ...

### iam-admin SecurityConfig

```
permitAll: /actuator/health
hasRole(ADMIN): /admin/api/**
anyRequest: authenticated
```

Filter 链：CORS → JwtAuthFilter。无 SAML/Kerberos。

## 7. 协议实现要点

### OAuth2 授权码 + PKCE

- `AuthCodeStore.issue(clientId, userId, redirectUri, scopes, codeChallenge, codeChallengeMethod, nonce)` 返回短 TTL 码。
- `/oauth/token` 校验 `code_verifier`：`BASE64URL(SHA256(code_verifier)) == codeChallenge`。
- 同时签发 access + refresh + id_token（scope 含 openid 时）。

### OIDC

- Discovery：`/.well-known/openid-configuration` 返回 issuer、endpoints、`code_challenge_methods_supported: ["S256"]`、`scopes_supported: ["openid","profile","email"]`。
- ID Token HS256，含 `nonce`。
- UserInfo 返回 sub、preferred_username、email、tenant。

### SAML 2.0

- `SamlConfig`：当 `iam.saml.idp.metadata-url` 配置时，用 `RelyingPartyRegistrations.fromMetadataLocation(url)`；否则占位 registration（启动告警）。
- `SamlConfig.SamlSuccessHandler`：登录成功后签发 JWT 并重定向到 `iam.saml.sp.frontend-redirect`。

### CAS 2.0

- `CasAuthService.authorizeUrl()`：重定向到 `{cas.server-url}/login?service={service-url}`。
- `callback(ticket)`：POST `{cas.server-url}/serviceValidate?ticket=...&service=...`，解析 XML `user`，签发 JWT。

### 社交登录

- `SocialLoginService.fetchProfile(provider, code)`：5 个 provider 各自的 token + userinfo HTTP 舞步（RestTemplate）。
- 回调签发 JWT 并重定向前端。

### SMS / Magic Link

- Redis 缓存：`sms:code:{phone}` TTL 5 分钟；`magic:token:{token}` TTL 15 分钟。
- 验证后 `loginOrProvision(phone, code, ip)`：找不到用户则按手机号自动 provision（可关闭）。

### LDAP

- `LdapConfig`：每租户独立 `LdapContextSource`，按 `user-dn-pattern` bind 验证密码。
- `iam.ldap.url` 为空时禁用，启动日志提示。

## 8. 扩展点

| 需求 | 改哪里 |
|------|-------|
| 接入真实 SMS | `SmsCodeService.send()` 替换 stub 调用阿里云/腾讯 SDK |
| 接入真实邮件 | `MagicLinkService.send()` 替换 stub |
| RS256 公钥 | `JwtTokenService` 注入 `KeyPair`，改 `SignatureAlgorithm.RS256`，JWKS 端点返回公钥 |
| 新社交 provider | `SocialLoginService` 加 `fetchProfile` 分支 |
| 新 OAuth2 grant | `OAuth2AuthService.token()` 加分支 |
| SCHEMA_PER_TENANT DDL | 在 `Liquibase` 之外加租户初始化 service，按 `tenants.code` 建 schema |
| WebAuthn 落地 | Spring Security 6.x（Boot 3.x）原生支持；当前 2.7 仅 stub |
| 新增审计事件 | 调用 `AuditLogService.record(principal, action, ip, ...)` |

## 9. 测试

| 测试类 | 覆盖 |
|--------|------|
| `AuthFlowTest` | 登录、刷新、MFA、失败锁定 |
| `LdapAuthTest` | 嵌入式 UnboundID LDAP 上下文 |
| `OAuth2FlowTest` | 授权码、PKCE（正确/错误）、client_credentials、bad secret、introspect、id_token |

测试 profile：`@ActiveProfiles("test")` + `application-test.yml` 用 H2 mem（MODE=MySQL）。
运行：`cd backend && mvn test`。

## 10. Profile 与 dev 数据源

| Profile | 用途 | 数据源 |
|---------|------|-------|
| (default) | 生产 / docker compose | MySQL（`application.yml`） |
| dev | 本地开发（`./scripts/dev.sh`） | H2 文件 + AUTO_SERVER=TRUE，`~/iam-dev/iam.*` |
| test | 单元/集成测试 | H2 内存（MODE=MySQL） |

**dev profile 关键点：**
- auth-server `application-dev.yml` 启 Liquibase + DemoSeeder，建表 + 种子。
- admin `application-dev.yml` **禁 Liquibase**（auth-server 已建表，避免启动竞态）。
- 两个服务用同一 JDBC URL `jdbc:h2:file:${user.home}/iam-dev/iam;AUTO_SERVER=TRUE;MODE=MySQL`，H2 文件锁机制允许多 JVM 共享。
- 数据持久化在磁盘；删 `~/iam-dev/iam.*` 即重置。
- H2 在两个 pom 里是 `runtime` scope（dev 运行时需要；生产 jar 多 ~2MB，可接受）。

## 11. 关键依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.7.18 | 框架基线 |
| Spring Security | 5.7 | 鉴权过滤链、SAML2 SP |
| spring-security-oauth2-autoconfigure | 2.6.8 | OAuth2 客户端配置 |
| spring-ldap-core | (Boot 管理) | LDAP bind |
| jjwt | 0.11.5 | JWT 签发/验证 |
| java-otp | 0.4.0 | TOTP（MFA） |
| Liquibase | (Boot 管理) | DB schema 版本管理 |
| MySQL Connector/J | 8.0.33 | JDBC |
| H2 | (Boot 管理) | dev/test 数据源 |
| Cola | 4.3.2 | catchlog + dto |
| Lombok | (Boot 管理) | 样板代码 |
| Vue | 3.4 | 前端框架 |
| Element Plus | 2.4 | UI 组件库 |
