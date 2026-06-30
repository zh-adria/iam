# IAM 统一身份认证与授权平台

基于 **Alibaba Cola 4.0 四层架构**（适配器 / 应用 / 领域 / 基础设施）+ Spring Boot 2.7 + Spring Security + OAuth2 + JPA 构建的保险中台 IAM。

## 快速开始

```bash
# 一键启动（MySQL + Redis + 后端 + 演示数据）
./scripts/start.sh

# 或本地开发模式
./scripts/dev.sh           # 后端 :8080
cd frontend && npm i && npm run dev   # 前端 :5173
```

**演示账号**
| 账号 | 密码 | 角色 |
|------|------|------|
| admin | Iam@2026 | ROLE_ADMIN |
| alice | User@2026 | ROLE_USER |

OAuth2 客户端：`demo-client` / `demo-secret`

## 服务拆分（auth-server / admin）

同一 jar，通过 Spring profile 切换运行模式：

```bash
# 认证服务（对外协议端点：登录/OAuth2/SAML/LDAP/CAS/社交/短信/Magic Link/SCIM/WebAuthn/Kerberos）
java -jar iam-platform-1.0.0-SNAPSHOT.jar --spring.profiles.active=authserver

# 管理态（用户/角色/权限/客户端/租户/审计/配置 CRUD）
java -jar iam-platform-1.0.0-SNAPSHOT.jar --spring.profiles.active=admin

# 默认（开发/测试）：两个 profile 都激活
java -jar iam-platform-1.0.0-SNAPSHOT.jar
```

控制器按 `@Profile` 条件装配：
- `authserver` → `AuthController`、`OAuth2Controller`、`UserController`（自服务）、`StubProtocolController`
- `admin` → `AdminController`（`/admin/api/**`，需 `ROLE_ADMIN`）

## 已实现协议矩阵

| 协议 | 状态 | 端点 |
|------|------|------|
| 密码登录 + JWT(HS256) | ✅ | `POST /api/auth/login` |
| MFA TOTP (RFC 6238) | ✅ | `/api/auth/mfa/verify`、`/api/users/mfa/*` |
| 失败锁定 + 限流 | ✅ | Redis 计数 |
| 多租户（共享 Schema） | ✅ | `iam_tenant` |
| RBAC + API/MENU/BUTTON/DATA | ✅ | `@PreAuthorize` |
| **OAuth2 授权码 + PKCE (S256)** | ✅ | `GET /oauth/authorize`、`POST /oauth/token` |
| **OAuth2 refresh_token grant** | ✅ | `POST /oauth/token` (轮换 + 撤销) |
| **OAuth2 password / client_credentials** | ✅ | `POST /oauth/token` |
| **OIDC Discovery + UserInfo + ID Token** | ✅ | `/.well-known/openid-configuration`、`/oauth/userinfo`、token 响应含 `id_token` |
| **RFC 7662 令牌内省** | ✅ | `POST /oauth/introspect` |
| **RFC 7009 令牌撤销** | ✅ | `POST /oauth/revoke` |
| JWKS | ✅ | `GET /oauth/jwks`（HS256 模式返回空数组） |
| SAML 2.0 SP | ✅ | `/saml2/authenticate/default`、`/login/saml2/sso/default` |
| **SAML IdP metadata URL 自动加载** | ✅ | `iam.saml.idp.metadata-url` |
| LDAP/AD 认证 | ✅ | `POST /api/auth/ldap` |
| **CAS SSO (CAS 2.0 serviceValidate)** | ✅ | `/api/auth/cas/{authorize,callback}` |
| **微信/支付宝/QQ/钉钉/企业微信** | ✅ | `/api/auth/social/{provider}/{authorize,callback}` |
| **短信验证码登录** | ✅ | `/api/auth/sms/{send,login}` |
| **Magic Link** | ✅ | `/api/auth/magic/{send,verify}` |
| 审计日志（哈希链防篡改） | ✅ | `iam_audit_log` |
| WebAuthn/FIDO2 | 🔶 stub | `/api/auth/webauthn/{register,auth}/{begin,finish}` — 需接入 spring-security-webauthn |
| Kerberos/SPNEGO | 🔶 stub | `/api/auth/kerberos` — 发 `WWW-Authenticate: Negotiate`，需 JAAS + krb5.conf |
| SCIM 2.0 | 🔶 stub | `/scim/v2/Users`、`/scim/v2/ServiceProviderConfigs` — 需实现 RFC 7643/7644 |
| OIDC RS256 + 真 JWKS | 🔶 | 当前 HS256 共享密钥；多 RS 验签时切换 |
| Schema-per-tenant | 🔶 | 建模已存，未实装 |
| ABAC SpEL 求值 | 🔶 | `PermissionEntity.spelExpression` 已存 |

> `ponytail:` 标注处为已知简化点与升级路径。

## 构建与测试

```bash
# 必须使用 JDK 17
export JAVA_HOME=/path/to/jdk17
mvn test          # 11 个测试全过
mvn package       # 产出 target/iam-platform-1.0.0-SNAPSHOT.jar

# 前端
cd frontend && npm i && npm run build   # 产出 frontend/dist/
```

## API 摘要

### 认证服务（authserver profile）
```
POST /iam/api/auth/login              { username, password, tenantCode } → TokenResponse
POST /iam/api/auth/ldap               { username, password }             → TokenResponse
POST /iam/api/auth/mfa/verify         { mfaToken, code }                 → TokenResponse
POST /iam/api/auth/refresh            { refreshToken }                   → TokenResponse
POST /iam/api/auth/logout             Authorization: Bearer ...
GET  /iam/api/auth/me                                                   → 当前主体

# 短信验证码
POST /iam/api/auth/sms/send           { phone }
POST /iam/api/auth/sms/login          { phone, code }                    → TokenResponse

# Magic Link
POST /iam/api/auth/magic/send         { email }
GET  /iam/api/auth/magic/verify       ?token=...                         → TokenResponse

# 社交登录（微信/支付宝/QQ/钉钉/企业微信）
GET  /iam/api/auth/social/{provider}/authorize                          → 重定向到 IdP
GET  /iam/api/auth/social/{provider}/callback?code=...                 → 重定向到前端带 token

# CAS SSO
GET  /iam/api/auth/cas/authorize                                       → 重定向到 CAS
GET  /iam/api/auth/cas/callback?ticket=...                             → 重定向到前端带 token

# 用户自服务
GET  /iam/api/users/me
POST /iam/api/users/register          { username, password, email, phone }
POST /iam/api/users/mfa/setup                                           → secret + otpauth URI
POST /iam/api/users/mfa/confirm       { code }
POST /iam/api/users/mfa/disable

# OAuth2 端点
GET  /iam/oauth/authorize             ?response_type=code&client_id=...&redirect_uri=...&scope=...&state=...&code_challenge=...&code_challenge_method=S256&nonce=...
POST /iam/oauth/token                 form: grant_type, client_id, client_secret, code/refresh_token/code_verifier/username/password, scope
GET  /iam/oauth/userinfo              → OIDC UserInfo
POST /iam/oauth/introspect            form: token, client_id, client_secret     → RFC 7662
POST /iam/oauth/revoke                form: token, client_id, client_secret     → RFC 7009
GET  /iam/oauth/.well-known/openid-configuration  → OIDC Discovery
GET  /iam/oauth/jwks                  → JWKS（HS256 模式为空）

# SAML SP
GET  /iam/saml2/authenticate/default  → SP 发起，重定向到 IdP
POST /iam/login/saml2/sso/default     → ACS，校验断言后重定向前端带 token

# SCIM 2.0（stub）
GET  /iam/scim/v2/Users
GET  /iam/scim/v2/ServiceProviderConfigs

# WebAuthn（stub）
POST /iam/api/auth/webauthn/{register,auth}/{begin,finish}

# Kerberos（stub）
GET  /iam/api/auth/kerberos           → 401 + WWW-Authenticate: Negotiate
```

### 管理态（admin profile，需 ROLE_ADMIN）
```
GET  /iam/admin/api/users             ?page&size&tenant
POST /iam/admin/api/users             { username, password, email, phone, tenantCode, status }
POST /iam/admin/api/users/{id}/reset-password   { password }
POST /iam/admin/api/users/{id}/status           { status }
POST /iam/admin/api/users/{id}/unlock
DELETE /iam/admin/api/users/{id}
POST /iam/admin/api/users/{id}/roles/{role}
DELETE /iam/admin/api/users/{id}/roles/{role}

GET  /iam/admin/api/roles             ?tenant
POST /iam/admin/api/roles             { code, name, tenantCode }
DELETE /iam/admin/api/roles/{code}

GET  /iam/admin/api/permissions
POST /iam/admin/api/permissions       { code, type, name, resource, action, spel }
DELETE /iam/admin/api/permissions/{code}
POST /iam/admin/api/roles/{role}/permissions/{perm}
DELETE /iam/admin/api/roles/{role}/permissions/{perm}

GET  /iam/admin/api/tenants
POST /iam/admin/api/tenants           { code, name, isolationMode, ldapUrl, ldapBase, enabled }
DELETE /iam/admin/api/tenants/{code}

GET  /iam/admin/api/oauth2/clients
POST /iam/admin/api/oauth2/clients    { clientId, clientSecret, grantTypes, redirectUris, scopes }
DELETE /iam/admin/api/oauth2/clients/{clientId}

GET  /iam/admin/api/audit             ?page&size&userId
GET  /iam/admin/api/config
```

## 架构分层（Cola 4.0 标准布局）

```
com.iam/
  adapter/
    controller/             # REST 控制器
      AuthController            /api/auth/*（密码/LDAP/MFA/社交/短信/Magic Link/CAS）
      OAuth2Controller          /oauth/*（授权码/PKCE/ID Token/introspect/revoke/JWKS/discovery）
      UserController           /api/users/*（自服务：注册/资料/MFA 绑定）
      AdminController          /admin/api/*（CRUD：用户/角色/权限/租户/客户端/审计/配置）
      StubProtocolController   WebAuthn/SCIM stub
      GlobalExceptionHandler
  app/
    service/
      AuthAppService           登录/MFA/刷新/登出主流程
      UserAppService           用户自服务 + MFA
      OAuth2AuthService        4 种 grant_type + PKCE + ID Token + introspect + revoke
      OAuth2ClientAppService   客户端注册
      LdapAuthService          LDAP/AD 绑定 + provisioning
      SocialLoginService       微信/支付宝/QQ/钉钉/企业微信统一回调
      SmsCodeService           短信验证码 + 手机号登录
      MagicLinkService         Magic Link 邮件令牌
      CasAuthService           CAS 2.0 service ticket 校验
      AdminAppService          管理态 CRUD + 配置
      LoginFailureRecorder     独立事务 bean 记录失败次数 + 锁定
    dto/                       ApiResult, TokenResponse, LoginCommand
  domain/
    AuthException
  infrastructure/
    entity/                    User/Role/Permission/OAuth2Client/RefreshToken/AuditLog/Tenant/SocialBinding
    repository/                Spring Data JPA 接口
    security/
      JwtTokenService          JWT 颁发/解析 + ID Token
      JwtAuthFilter            Bearer token 过滤器
      TotpService              RFC 6238 TOTP
      TokenCacheService        Redis 缓存 + 限流 + 锁定 + SMS/Magic 令牌
      AuthCodeStore            OAuth2 授权码内存存储（含 PKCE 字段）
      PasswordHasher           BCrypt
      AuditLogService          异步审计 + 哈希链
      KerberosSpnegoFilter     SPNEGO 401 challenge（stub）
    ldap/
      LdapConfig               LdapContextSource + 运行时重配
  start/
    IamApplication             统一启动类（测试 + 默认双 profile）
    DemoSeeder                 启动播种
    config/
      SecurityConfig           过滤链 + 路径授权 + profile 无关
      SamlConfig               RelyingPartyRegistration + metadata URL 自动加载 + SamlSuccessHandler
```

## 前端

```
frontend/
  src/
    api/
      index.ts               认证 API（密码/社交/短信/Magic Link/CAS）+ saveSession/hasRole
      admin.ts               管理 API（用户/角色/权限/租户/客户端/审计/配置）
    views/
      LoginView.vue          6 Tab：密码/短信/Magic Link/社交/企业SSO/OAuth2
      DashboardView.vue      用户资料 + MFA 绑定 + 管理后台入口
      MfaView.vue            TOTP 绑定流程
      MagicCallbackView.vue  Magic Link 回调
      OAuth2CallbackView.vue OAuth2 授权码回调
      SocialCallbackView.vue 社交/CAS 回调
      admin/
        AdminView.vue        管理后台布局（侧边栏）
        panes/
          UsersPane.vue      用户 CRUD + 重置密码 + 解锁 + 角色分配
          RolesPane.vue      角色 CRUD
          PermsPane.vue      权限 CRUD + 角色授权
          ClientsPane.vue    OAuth2 客户端 CRUD
          TenantsPane.vue    租户 CRUD（含 LDAP 配置）
          AuditPane.vue      审计日志（哈希链）
          ConfigPane.vue     系统配置总览
    router/index.ts          路由 + admin 守卫
```

## 数据库表

| 表 | 用途 |
|----|------|
| iam_tenant | 租户（隔离模式、LDAP 配置） |
| iam_user | 用户（BCrypt 密码、MFA 密钥、锁定状态） |
| iam_role | 角色 |
| iam_permission | 权限（API/MENU/BUTTON/DATA + SpEL） |
| iam_user_role | 用户-角色 |
| iam_role_permission | 角色-权限 |
| iam_oauth2_client | OAuth2 客户端 |
| iam_refresh_token | 刷新令牌（轮换 + 撤销） |
| iam_audit_log | 审计日志（哈希链防篡改） |
| iam_social_binding | 社交账号绑定 |

## 安全要点

- 密码 BCrypt（强度可配，默认 10）
- 登录失败 5 次锁定 30 分钟（Redis 计数）
- 单 IP 登录频率限流（默认 10 次/分钟）
- JWT 短期 + RefreshToken 长期 + 服务端撤销（Redis）
- PKCE S256 强制（公开客户端防授权码截持）
- 敏感信息脱敏：手机 `138****8000`、邮箱 `a***@x.com`
- 审计日志 SHA-256 哈希链，事后篡改可检测
- CORS 白名单 + CSRF 关闭（无状态 JWT）
- 全局异常处理，错误码不泄露内部细节

## 部署

`docker-compose up -d --build` 一键启动 MySQL + Redis + 后端。前端 `npm run build` 后用任意静态服务器托管，反向代理 `/iam/*` 到后端。

## 升级路径（ponytail 简化点）

- HS256 → RS256 + 真 JWKS：多资源服务器独立验签时切换
- InMemoryRelyingPartyRegistrationRepository → JPA backed：多 IdP 动态注册
- LDAP 单服务器 → 按租户多服务器：`TenantEntity.ldapUrl` 已建模，建 per-tenant LdapContextSource
- Schema-per-tenant：Hibernate multi-tenant + `TenantInterceptor`
- ABAC SpEL：`PermissionEntity.spelExpression` 已存，接 `MethodSecurityExpressionHandler`
- WebAuthn：接入 `spring-security-webauthn`（Spring Boot 2.7 不内置）
- Kerberos：JAAS krb5 登录配置 + `org.ietf.jgss` 解 SPNEGO token
- SCIM：接 `scim2-sdk` 实现 `/scim/v2/Users` 全 CRUD
- 短信/Magic Link sender：替换 `SmsCodeService.send` / `MagicLinkService.send` 中的 console stub
- 社交登录 Alipay：接 `alipay-sdk-java` 做 RSA2 签名
