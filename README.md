# IAM 统一身份认证与授权平台

基于 **Alibaba Cola 4.0 四层架构**（适配器 / 应用 / 领域 / 基础设施）+ Spring Boot 2.7 + Spring Security + OAuth2 + JPA 构建的统一身份认证与授权平台。

## 快速开始

```bash
# 开发模式（需先配置 IAM_CONFIG_KEY，见下方「加密配置」）
.\scripts\dev.ps1              # Windows（推荐）
./scripts/dev.sh               # Linux / Git Bash

# 仅后端
.\scripts\dev-backend.ps1      # Windows
./scripts/dev-backend.sh       # Linux

# 仅前端
.\scripts\dev-frontend.ps1     # Windows
./scripts/dev-frontend.sh      # Linux

# Docker 部署（需 IAM_CONFIG_KEY）
.\scripts\start.ps1             # Windows
./scripts/start.sh              # Linux
```

## 加密配置

数据库连接参数存储在 `ENC()` 加密格式中。启动前必须配置解密密钥。

### 首次设置（只需一次）

```powershell
# 1. 设置你的解密密钥（记住它，后续启动都需要）
$env:IAM_CONFIG_KEY = "my-super-secret-key-2026"

# 2. 运行加密脚本生成 ENC() 值
.\scripts\encrypt-config.ps1 -ConfigKey $env:IAM_CONFIG_KEY

# 3. 将输出的 3 行加密值复制到 4 个配置文件中的对应位置：
#    - backend/iam-auth-server/src/main/resources/application-dev.yml
#    - backend/iam-auth-server/src/main/resources/application.yml
#    - backend/iam-admin/src/main/resources/application-dev.yml
#    - backend/iam-admin/src/main/resources/application.yml
```

### 持久化密钥

为了避免每次重启都输入密钥，将其保存到本地（git 不会提交）：

```powershell
# PowerShell 会加密存储到 scripts/.secrets/iam-config-key.txt
.\scripts\dev.ps1
# 首次运行时会提示输入密钥并自动保存
```

或通过环境变量永久设置：
```powershell
[System.Environment]::SetEnvironmentVariable("IAM_CONFIG_KEY", "my-super-secret-key-2026", "User")
```

**演示账号**

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | Iam@2026 | ROLE_ADMIN |
| alice | User@2026 | ROLE_USER |

OAuth2 客户端：`demo-client` / `demo-secret`

## 数据库表前缀

表名使用 `auth_` 前缀区分认证核心功能线，`admin_` 前缀区分管理后台功能线：

| 前缀 | 归属 | 示例 |
|------|------|------|
| `auth_` | auth-server 认证核心 | `auth_user`, `auth_role`, `auth_permission`, `auth_oauth2_client`, `auth_refresh_token`, `auth_audit_log`, ... |
| `admin_` | admin-server 管理后台 | `admin_system_config` |

## 服务模式（同一 jar，按 profile 切换）

| 模式 | 端点 | 用途 |
|------|------|------|
| `authserver` profile | `http://localhost:8080/iam/*` | 登录 / OAuth2 / SAML / OIDC / LDAP / CAS / 社交 / 短信 / Magic Link |
| `admin` profile | `http://localhost:8081/iam/admin/api/*` | CRUD（用户 · 角色 · 权限 · 客户端 · 租户 · 审计 · 配置） |
| 默认（双激活） | 上述全部 | 开发 / 测试 |

```bash
java -jar iam-platform-1.0.0-SNAPSHOT.jar --spring.profiles.active=authserver
java -jar iam-platform-1.0.0-SNAPSHOT.jar --spring.profiles.active=admin
java -jar iam-platform-1.0.0-SNAPSHOT.jar               # 默认开发模式
```

控制器按 `@Profile` 条件装配：
- `authserver` → `AuthController` / `OAuth2Controller` / `UserController`（自服务）/ `StubProtocolController`
- `admin` → `AdminController`（需 `ROLE_ADMIN`）

## 已实现协议矩阵

| 协议 | 状态 | 端点 |
|------|------|------|
| 密码登录 + JWT (RS256) | ✅ | `POST /iam/api/auth/login` |
| MFA TOTP (RFC 6238) | ✅ | `/iam/api/auth/mfa/verify`、`/iam/api/users/mfa/*` |
| 失败锁定 + 限流 | ✅ | Redis 计数 |
| 多租户（SHARED / SCHEMA / DATABASE） | ✅ | `iam_tenant` |
| RBAC + API / MENU / BUTTON / DATA | ✅ | `@PreAuthorize` |
| **OAuth2 授权码 + PKCE (S256)** | ✅ | `GET /iam/oauth/authorize`、`POST /iam/oauth/token` |
| **OAuth2 refresh_token 轮换** | ✅ | `POST /iam/oauth/token` |
| **OAuth2 password / client_credentials** | ✅ | `POST /iam/oauth/token` |
| **OIDC Discovery / UserInfo / ID Token** | ✅ | `/.well-known/openid-configuration`、`/iam/oauth/userinfo` |
| **RFC 7662 令牌内省** | ✅ | `POST /iam/oauth/introspect` |
| **RFC 7009 令牌撤销** | ✅ | `POST /iam/oauth/revoke` |
| JWKS (RS256) | ✅ | `GET /iam/oauth/jwks` |
| SAML 2.0 SP | ✅ | `/iam/saml2/authenticate/{regId}`、`/iam/login/saml2/sso/{regId}` |
| SCIM 2.0 占位 | ✅ | `/iam/scim/v2/*` |
| 社交登录（微信 / 支付宝 / QQ / 钉钉 / 企微） | ✅ | `/iam/api/auth/social/{provider}/{authorize,callback}` |
| 短信验证码 / Magic Link / CAS | ✅ | 完整流程（stub 发送器） |
| LDAP/AD 认证 | ✅ | `POST /iam/api/auth/ldap` |
| **ABAC SpEL 求值** | ✅ | `@PreAuthorize` 注入 `#uid #tenant #roles #target #action` |
| **审计日志（SHA-256 哈希链）** | ✅ | `iam_audit_log` |
| Schema-per-tenant | ✅ | `isolation_mode='SCHEMA_PER_TENANT'` |
| 按租户多 LDAP | ✅ | `TenantEntity.ldapUrl` + 运行时 LdapTemplate 工厂 |
| 短信 / SMTP / 支付宝 SDK | ✅ | 可插拔接口（`SmsSender` / `MagicSender`） |
| SAML IdP 多租户注册 | ✅ | `iam_saml_idp_registration` |
| WebAuthn / Kerberos / SCIM 2.0 全 CRUD | 🔶 | 需接入三方库 |

> `ponytail:` 标注处为已知简化点与升级路径。

## 前端（Vue 3 + Element Plus + TypeScript）

### 设计系统（`frontend/src/styles/global.css`）

品牌 violet `#5b4dff`，字体 **Inter（UI）+ Syne（显示）+ JetBrains Mono（代码）**。

```
:root {
  --accent    : #5b4dff;        /* 主色：按钮 / 焦点 / active */
  --accent-dim: #4a3fcf;        /* hover */
  --bg-wash   : #f6f8fb;        /* admin 背景灰 */
  --border    : #e4e8ef;
  --shadow-sm / --shadow-md / --shadow-lg / --shadow-xl;   /* 四档深度 */
  --radius-sm: 4px; --radius-md: 8px;
  --radius-lg: 10px; --radius-xl: 14px; --radius-pill: 9999px;
}
```

### 页面结构

| 路由 | 页面 | 布局 |
|------|------|------|
| `/login` | LoginView | 左右分屏（左侧深紫 hero，右侧玻璃卡片）+ tab 芯片切换 6 种认证方式 |
| `/dashboard` | DashboardView | 顶部栏 + KPI 数卡 + 用户信息 / 角色 / 权限三栏 + 快捷操作 |
| `/mfa` | MfaView | 6 格 OTP 输入，自动跳格 |
| `/admin` | AdminView | 深色 `#0b0d1f` sidebar + 7 个 pane 切换 |
| `/callback`, `/magic-callback`, `/social-callback` | CallbackView | 新式 8 点圆环绕动 spinner |

### 公共组件

- `components/PaneToolbar.vue` — 搜索 + 新建 + 自定义 action 插槽
- `components/StatCard.vue` — KPI 卡（value / delta / icon）
- `components/EmptyState.vue` — 通用空状态
- `components/CallbackView.vue` — 回调等待页

### Admin pane 功能

| Pane | 关键能力 |
|------|----------|
| 用户管理 `UsersPane` | 租户过滤 · 新建 · 重置密码 · 启用/禁用 · 解锁 · 删除 · **角色分配 / 撤销多选** |
| 角色管理 `RolesPane` | 新建 · 删除 · **关联权限网格授权** |
| 权限管理 `PermsPane` | 新建 (API/MENU/BUTTON/DATA/SpEL) · 删除 · 角色授权对话框 |
| OAuth2 客户端 `ClientsPane` | client_id / secret / grant_types **多选** / redirect_uris / scopes / 创建编辑删除 |
| 租户管理 `TenantsPane` | SHARED / SCHEMA / DATABASE 三模式 · Schema · LDAP · 创建编辑删除 |
| 审计日志 `AuditPane` | userId 过滤 · 动作 · 结果 · 前序哈希 · 时间 |
| 系统配置 `ConfigPane` | 认证协议开关 + KV 参数，双 tab 切换 |

### 开发命令

```bash
cd frontend
npm run dev            # vite, :5173
npm run build          # 生产构建
npm run build:strict   # vue-tsc 强类型 + vite build
```

后端：

```bash
mvn clean package -DskipTests
java -jar backend/iam-auth-server/target/iam-platform-1.0.0-SNAPSHOT.jar
```

## API 摘要

### 认证服务（authserver profile）

```
POST   /iam/api/auth/login                { username, password, tenantCode } → TokenResponse
POST   /iam/api/auth/ldap                 { username, password }             → TokenResponse
POST   /iam/api/auth/mfa/verify           { mfaToken, code }                  → TokenResponse
POST   /iam/api/auth/refresh              { refreshToken }                    → TokenResponse
POST   /iam/api/auth/logout               Authorization: Bearer ...
GET    /iam/api/auth/me                                                       → 当前主体

# MFA
POST   /iam/api/users/mfa/setup                                               → secret + otpauth URI
POST   /iam/api/users/mfa/confirm             { code }
POST   /iam/api/users/mfa/disable

# 短信验证码
POST   /iam/api/auth/sms/send               { phone }
POST   /iam/api/auth/sms/login              { phone, code }                  → TokenResponse

# Magic Link
POST   /iam/api/auth/magic/send             { email }
GET    /iam/api/auth/magic/verify           ?token=...                       → TokenResponse

# 社交登录（微信/支付宝/QQ/钉钉/企业微信）
GET    /iam/api/auth/social/{provider}/authorize                             → 重定向到 IdP
GET    /iam/api/auth/social/{provider}/callback?code=...                    → 重定向到前端带 token

# CAS SSO
GET    /iam/api/auth/cas/authorize                                             → 重定向到 CAS
GET    /iam/api/auth/cas/callback?ticket=...                                  → 重定向前端带 token

# OAuth2 端点
GET    /iam/oauth/authorize               ?response_type=code&client_id=...&redirect_uri=...&scope=...&code_challenge=...&code_challenge_method=S256
POST   /iam/oauth/token                   form: grant_type, client_id, client_secret, code/refresh_token/code_verifier/username/password
GET    /iam/oauth/userinfo                → OIDC UserInfo
POST   /iam/oauth/introspect              → RFC 7662
POST   /iam/oauth/revoke                  → RFC 7009
GET    /iam/oauth/.well-known/openid-configuration
GET    /iam/oauth/jwks                    → JWKS（RS256 公钥）

# SAML SP
GET    /iam/saml2/authenticate/{regId}
POST   /iam/login/saml2/sso/{regId}

# 用户自服务
GET    /iam/api/users/me
POST   /iam/api/users/register            { username, password, email, phone }
```

### 管理态（admin profile）

```
# 用户
GET    /iam/admin/api/users               ?page&size&tenant
POST   /iam/admin/api/users               { username, password, email, phone, tenantCode, status }
POST   /iam/admin/api/users/{id}/reset-password     { password }
POST   /iam/admin/api/users/{id}/status   { status }
POST   /iam/admin/api/users/{id}/unlock
DELETE /iam/admin/api/users/{id}
POST   /iam/admin/api/users/{id}/roles/{role}
DELETE /iam/admin/api/users/{id}/roles/{role}

# 角色
GET    /iam/admin/api/roles               ?tenant
POST   /iam/admin/api/roles               { code, name, tenantCode }
DELETE /iam/admin/api/roles/{code}

# 权限
GET    /iam/admin/api/permissions
POST   /iam/admin/api/permissions         { code, type, name, resource, action, spel }
DELETE /iam/admin/api/permissions/{code}
POST   /iam/admin/api/roles/{role}/permissions/{perm}
DELETE /iam/admin/api/roles/{role}/permissions/{perm}

# 租户
GET    /iam/admin/api/tenants
POST   /iam/admin/api/tenants             { code, name, isolationMode, schemaName, ldapUrl, ldapBase, enabled }
DELETE /iam/admin/api/tenants/{code}

# OAuth2 客户端
GET    /iam/admin/api/oauth2/clients
POST   /iam/admin/api/oauth2/clients      { clientId, clientSecret, grantTypes, redirectUris, scopes }
DELETE /iam/admin/api/oauth2/clients/{clientId}

# 审计日志
GET    /iam/admin/api/audit               ?page&size&userId

# 系统配置
GET    /iam/admin/api/config

# SAML IdP 多租户注册
GET    /iam/admin/api/saml/idps           ?tenant
POST   /iam/admin/api/saml/idps           { tenantCode, registrationId, idpEntityId, idpSsoUrl, idpMetadataUrl, spEntityId, acsTemplate, enabled }
DELETE /iam/admin/api/saml/idps/{tenantCode}/{registrationId}
```

## 前端文件结构

```
frontend/
  src/
    api/
      index.ts               认证 API + saveSession / hasRole
      admin.ts               管理 API
    components/
      PaneToolbar.vue        搜索 + 新建 + action 插槽
      StatCard.vue           KPI 数卡
      EmptyState.vue         通用空状态
      CallbackView.vue       回调等待页
    styles/
      global.css             Design tokens（colors / shadow / radius / Element Plus 覆盖）
    views/
      LoginView.vue          左右分屏登录
      DashboardView.vue      KPI + 用户卡 + 快捷操作
      MfaView.vue            6 格 OTP
      MagicCallbackView.vue
      OAuth2CallbackView.vue
      SocialCallbackView.vue
      admin/
        AdminView.vue        深色 sidebar + pane 切换
        panes/
          UsersPane.vue      ← 角色分配多选
          RolesPane.vue      ← 关联权限网格
          PermsPane.vue
          ClientsPane.vue    ← grant_types 多选 / secret
          TenantsPane.vue
          AuditPane.vue
          ConfigPane.vue     ← 双 tab
    router/index.ts          路由 + admin 守卫（hasRole）
```

## 架构分层（Cola 4.0 标准布局）

```
com.iam/
  adapter/
    controller/             # REST 控制器
      AuthController            /api/auth/*
      OAuth2Controller          /oauth/*
      UserController           /api/users/*
      AdminController          /admin/api/*
      StubProtocolController   WebAuthn/SCIM stub
      GlobalExceptionHandler
  app/
    service/
      AuthAppService           登录/MFA/刷新/登出主流程
      UserAppService           用户自服务 + MFA
      OAuth2AuthService        4 种 grant_type + PKCE + ID Token + introspect + revoke
      OAuth2ClientAppService   客户端注册
      LdapAuthService          LDAP/AD 绑定 + provisioning
      SocialLoginService       社交登录统一回调
      SmsCodeService           短信验证码 + 手机号登录
      MagicLinkService         Magic Link 令牌
      CasAuthService           CAS 2.0 serviceValidate
      AdminAppService          管理态 CRUD + 配置
      LoginFailureRecorder     失败计数 + 锁定
    dto/                       ApiResult, TokenResponse, LoginCommand
  domain/
    AuthException
  infrastructure/
    entity/                    User/Role/Permission/OAuth2Client/RefreshToken/AuditLog/Tenant/SocialBinding
    repository/                Spring Data JPA
    security/
      JwtTokenService          RS256 JWT + ID Token + JWK
      JwtAuthFilter            Bearer 过滤器 + 租户上下文注入
      TotpService              RFC 6238 TOTP
      TokenCacheService        Redis 缓存 + 限流 + 锁定 + SMS/Magic 令牌
      AuthCodeStore            OAuth2 授权码 + PKCE 字段
      PasswordHasher           BCrypt
      AuditLogService          异步审计 + 哈希链
      AbacPermissionEvaluator / AbacMethodSecurityExpressionHandler
    ldap/ LdapConfig
    sms/   SmsSender / StubSmsSender / AliyunSmsSender
    magiclink/ MagicSender / StubMagicSender / SmtpMagicSender
    tenant/ CurrentTenantHolder / TenantAwareConnectionProvider / SchemaRoutingDataSource
  start/
    IamApplication
    DemoSeeder
    config/ SecurityConfig / SamlConfig
```

## 数据库表

| 表 | 用途 |
|----|------|
| `auth_tenant`             | 租户（隔离模式、Schema、LDAP 配置） |
| `auth_user`               | 用户（BCrypt 密码、MFA 密钥、锁定状态） |
| `auth_role`               | 角色 |
| `auth_permission`         | 权限（API/MENU/BUTTON/DATA + SpEL） |
| `auth_user_role`          | 用户-角色多对多 |
| `auth_role_permission`    | 角色-权限多对多 |
| `auth_oauth2_client`      | 注册客户端 |
| `auth_refresh_token`      | 刷新令牌（轮换 + 撤销） |
| `auth_audit_log`          | 审计日志（SHA-256 哈希链） |
| `auth_social_binding`     | 社交账号绑定 |
| `auth_saml_idp_registration` | SAML IdP 多租户注册 |
| `auth_api_key`            | API Key |
| `admin_system_config`     | 系统配置（KV） |

## 安全要点

- BCrypt 密码哈希（强度 10，可配）
- 失败 5 次锁定 30 分钟（Redis 计数）
- 单 IP 限流 10 次/分钟
- JWT 短期 + RefreshToken 轮换 + 服务端撤销（Redis）
- PKCE S256 强制（防授权码截持）
- 用户信息脱敏：手机 `138****8000`、邮箱 `a***@x.com`
- 审计 SHA-256 哈希链，事后篡改可检测
- CORS 白名单 + CSRF 关闭（无状态 JWT）
- 全局异常处理，错误码不泄露内部细节

## 部署

### Docker Compose（推荐）

```bash
# 1. 复制环境变量模板
cp .env.example .env
# 编辑 .env，设置 IAM_CONFIG_KEY

# 2. 启动（含本地 MySQL + Redis + 两个服务）
docker compose up -d --build

# 或仅启动应用（使用远程 MySQL）
docker compose up -d --build iam-redis iam-auth iam-admin
```

### 手动部署

```bash
# 构建
mvn clean package -DskipTests
cd frontend && npm run build

# 启动（需要 IAM_CONFIG_KEY 环境变量）
java -jar backend/iam-auth-server/target/boot/iam-auth-server.jar --spring.profiles.active=prod
java -jar backend/iam-admin/target/boot/iam-admin.jar --spring.profiles.active=prod
```

## 脚本速查

| 任务 | Linux/Mac/Git Bash | Windows PowerShell |
|------|--------------------|--------------------|
| 开发模式（完整栈） | `./scripts/dev.sh` | `.\scripts\dev.ps1` |
| 仅后端 | `./scripts/dev-backend.sh` | `.\scripts\dev-backend.ps1` |
| 仅前端 | `./scripts/dev-frontend.sh` | `.\scripts\dev-frontend.ps1` |
| 加密数据库配置 | — | `.\scripts\encrypt-config.ps1` |
| Docker 部署 | `./scripts/start.sh` | `.\scripts\start.ps1` |
| 停止一切 | `./scripts/stop.sh` | `.\scripts\stop.ps1` |
| 仅构建 | `./scripts/build.sh` | `.\scripts\build.ps1` |

## 文档索引

- [产品需求手册](./docs/产品需求手册.md)
- [设计手册](./docs/设计手册.md)
- [技术手册](./docs/技术手册.md)            技术手册（架构、模块）
- [协议手册](./docs/协议手册.md) 13 种认证协议操作手册（curl 示例）
- [操作手册](./docs/操作手册.md)            部署 / 配置 / 故障排查
- [技术债务](./docs/技术债务.md)          已知简化与升级路径

## 升级路径

已落地：RS256 + JWKS · ABAC SpEL · Schema-per-tenant · 按租户多 LDAP · 短信/Magic/支付宝 SDK · JPA SAML IdP 注册。
待接入：WebAuthn · Kerberos · SCIM 2.0 全 CRUD · 支付宝生产依赖显式引入。

## 许可

MIT License — see [LICENSE](./LICENSE) for details.
