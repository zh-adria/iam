# IAM Platform — 操作手册

> 版本：1.1.0 · 更新：2026-07-04

## 1. 环境准备

### 必备

| 软件 | 版本 | 备注 |
|------|------|------|
| JDK | 17 | `JAVA_HOME` 指向 17 |
| Maven | 3.9+ | `mvn -v` 验证 |
| Docker | 24+ | 含 docker compose v2 |
| Node.js | 20+ | `npm -v` 验证（构建前端用） |
| MySQL | 8.0 | docker compose 自带 |
| Redis | 7+ | docker compose 自带 |

Windows 环境变量（PowerShell）：
```powershell
$env:JAVA_HOME = 'D:\Program Files (x86)\jdk17'
$env:PATH = "$env:JAVA_HOME\bin;D:\Program Files (x86)\apache-maven-3.9.7\bin;$env:PATH"
```

> 脚本里已默认这两个路径，若你的安装不同请改 `$env:JAVA_HOME` 或直接改 `.ps1` 顶部的默认值。

### 脚本版本

每个脚本都有两个版本：
- `scripts/*.sh` — Git Bash / WSL / Linux / macOS
- `scripts/*.ps1` — Windows PowerShell（Win10/11 自带，无需 Git Bash）

| 任务 | Linux/Mac/Git Bash | Windows PowerShell |
|------|--------------------|--------------------|
| 开发模式（H2 + vite） | `./scripts/dev.sh` | `.\scripts\dev.ps1` |
| 生产 docker compose | `./scripts/start.sh` | `.\scripts\start.ps1` |
| 停止一切 | `./scripts/stop.sh` | `.\scripts\stop.ps1` |
| 仅构建 | `./scripts/build.sh` | `.\scripts\build.ps1` |
| 仅后端 dev | `./scripts/dev-backend.sh` | `.\scripts\dev-backend.ps1` |
| 仅前端 dev | `./scripts/dev-frontend.sh` | `.\scripts\dev-frontend.ps1` |

### Windows 首次运行 PowerShell 脚本

PowerShell 默认禁止运行 `.ps1`。两种解决方式（任选其一）：

**方式 A：永久放开当前用户（推荐，做一次）**
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
# 之后直接运行：
.\scripts\dev.ps1
```

**方式 B：单次绕过（不改策略）**
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1
```

## 2. 一键启动

### 2.1 开发模式（本地 jar + vite dev）

Linux / macOS / Git Bash：
```bash
./scripts/dev.sh
```
Windows PowerShell：
```powershell
.\scripts\dev.ps1
```

- 仅拉起 redis（docker），**不启动 mysql** — 后端用 H2 文件库
- `mvn -DskipTests package` 构建后端
- 后台启动 iam-auth-server（8080，`--spring.profiles.active=dev`）
- 等 auth-server health 通过后再起 iam-admin（8081，共享同一份 H2 文件）
- 前台启动 vite dev server（5173）
- Ctrl-C 一次性停掉所有进程 + redis 容器

数据存储：`~/iam-dev/iam.*`（Linux/Mac）或 `%USERPROFILE%\iam-dev\iam.*`（Windows）。H2 文件，AUTO_SERVER=TRUE 让两个 JVM 共享一份库。**删除即重置**。
日志：`logs/auth-server.log`、`logs/admin.log`。

> 切回 MySQL dev：删 `application-dev.yml` 或把 datasource URL 改回 `jdbc:mysql://...` 并在 dev 脚本里 `docker compose up -d mysql redis`。

### 2.2 生产模式（docker compose 全栈）

```bash
./scripts/start.sh           # Linux/Mac/Git Bash
.\scripts\start.ps1          # Windows PowerShell
```

- 构建两个后端镜像（同一 Dockerfile，`SERVICE` arg 切换）
- 启动 mysql + redis + iam-auth-server + iam-admin
- 前端单独用 nginx / CDN 部署（生产不走 vite）

### 2.3 单独后端 / 单独前端

```bash
./scripts/dev-backend.sh     # Linux/Mac/Git Bash
./scripts/dev-frontend.sh
.\scripts\dev-backend.ps1    # Windows PowerShell
.\scripts\dev-frontend.ps1
```

### 2.4 停止一切

```bash
./scripts/stop.sh            # Linux/Mac/Git Bash
.\scripts\stop.ps1           # Windows PowerShell
```

### 2.5 仅构建不启动

```bash
./scripts/build.sh           # Linux/Mac/Git Bash
.\scripts\build.ps1          # Windows PowerShell
```

产出：
- `backend/iam-auth-server/target/iam-auth-server-1.0.0-SNAPSHOT.jar`
- `backend/iam-admin/target/iam-admin-1.0.0-SNAPSHOT.jar`
- `frontend/dist/`

## 3. 访问入口

| 服务 | URL | 用途 |
|------|-----|------|
| 前端 | http://localhost:5173 | 登录页 + 管理控制台 |
| Auth Server | http://localhost:8080/iam | 运行态 API |
| Admin Server | http://localhost:8081/iam | 管理态 API |
| H2 dev 数据 | `~/iam-dev/iam.*`（Linux/Mac）或 `%USERPROFILE%\iam-dev\iam.*`（Windows） | dev profile 文件库（AUTO_SERVER 共享） |
| MySQL | localhost:3306 | db=iam user=iam pwd=iam123 |
| Redis | localhost:6379 | 无密码 |
| 健康检查 | http://localhost:8080/iam/actuator/health | auth-server |
| 健康检查 | http://localhost:8081/iam/actuator/health | admin |

## 4. 演示账号

| 用途 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | Iam@2026 |
| 普通用户 | alice | User@2026 |
| OAuth2 客户端 | demo-client | demo-secret |

种子数据：`backend/iam-auth-server/src/main/java/com/iam/start/DemoSeeder.java`，启动时自动注入。

## 5. 环境变量

后端服务通用：

| 变量 | 默认 | 说明 |
|------|------|------|
| `DB_HOST` | localhost | MySQL 主机 |
| `DB_USER` | iam | MySQL 用户 |
| `DB_PASS` | iam123 | MySQL 密码 |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PASS` | （空） | Redis 密码 |
| `IAM_JWT_SECRET` | CHANGE_ME_... | **两个服务必须相同**，否则 admin 验不了 auth-server 签的 JWT |
| `OAUTH_GOOGLE_ID` / `_SECRET` | （空） | Google OIDC 客户端 |
| `IAM_JWT_ACCESS_TTL_MINUTES` | 30 | access 令牌 TTL |
| `IAM_JWT_REFRESH_TTL_DAYS` | 7 | refresh 令牌 TTL |

完整配置见 `backend/iam-auth-server/src/main/resources/application.yml`。

## 6. 常见运维任务

### 6.1 重置管理员密码

数据库直接改（BCrypt strength=10）：

```sql
-- 在 mysql 容器里执行
UPDATE users SET password_hash='$2a$10$...' , status=1
WHERE username='admin';
```

或用 BCrypt 工具生成新 hash：
```bash
java -cp backend/iam-auth-server/target/iam-auth-server-1.0.0-SNAPSHOT.jar \
  org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder "NewPwd@2026"
```

### 6.2 轮换 JWT 密钥

1. 生成新密钥（≥32 字节）：
   ```bash
   openssl rand -base64 48
   ```
2. 同时更新两个服务的 `IAM_JWT_SECRET` 环境变量。
3. 滚动重启：先重启 auth-server，再重启 admin。
4. 旧 token 在刷新时换发为新密钥签的；未刷新的 access 在原 TTL 内继续可用（如需立即失效，把旧 jti 加入 `TokenCacheService` 黑名单）。

### 6.3 解锁被锁定的用户

```sql
UPDATE users SET status=1 WHERE username='alice';
```

或调 admin API：`POST /iam/admin/api/users/{id}/unlock`。

### 6.4 备份

```bash
# MySQL
docker exec iam-mysql sh -c 'mysqldump -uroot -proot123 iam' > backup-$(date +%F).sql

# Redis（AOF 已开）
docker cp iam-redis:/data ./redis-backup-$(date +%F)
```

### 6.5 查看日志

```bash
# docker 部署
docker compose logs -f iam-auth-server
docker compose logs -f iam-admin

# 本地 dev
tail -f logs/auth-server.log
tail -f logs/admin.log
```

### 6.6 进入数据库

```bash
docker exec -it iam-mysql mysql -uiam -piam123 iam
```

### 6.7 清空 Redis 缓存

```bash
docker exec -it iam-redis redis-cli FLUSHDB
```

> 谨慎：会清掉所有 SMS 码、Magic Token、限流计数器、access token 黑名单。

## 7. 健康检查与监控

- Liveness：`GET /iam/actuator/health` 返回 `{"status":"UP"}`。
- 关键依赖：响应里包含 `db`、`redis` 组件状态。
- 接入 Prometheus：加 `spring-boot-starter-actuator` 已在依赖，暴露 `/iam/actuator/prometheus`（需在 yml 开启 `management.endpoints.web.exposure.include`）。

## 8. 端口冲突排查

| 端口 | 占用者 | 排查命令 |
|------|--------|---------|
| 8080 | auth-server | `netstat -ano \| findstr 8080`（Win）/ `lsof -i:8080`（Mac/Linux） |
| 8081 | admin | 同上 |
| 3306 | mysql | docker ps / 本机 MySQL 服务 |
| 6379 | redis | docker ps / 本机 Redis 服务 |
| 5173 | vite | npm 进程 |

释放端口：

Windows PowerShell：
```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess
Stop-Process -Id <pid> -Force
```

Linux / macOS：
```bash
lsof -i:8080
kill -9 <pid>
```

## 9. 故障排查

### 9.1 启动报「Public key retrieval is not allowed」

MySQL 8 默认 caching_sha2_password，JDBC 首次握手需要公钥。
解决：URL 已带 `allowPublicKeyRetrieval=true`；若仍报错，检查 `DB_USER` 是否为非 root 用户，或加 `--default-authentication-plugin=mysql_native_password` 到 mysql 启动参数。

### 9.2 Liquibase 锁

异常中断后 `databasechangeloglock` 表残留锁：
```sql
UPDATE databasechangeloglock SET locked=0;
```

### 9.3 前端登录 401 但后端日志正常

检查 `vite.config.ts` 代理：`/iam/admin/*` → 8081，`/iam/*` → 8080。顺序不能反（更具体的在前）。

### 9.4 admin 接口 403

JWT 里没有 `ROLE_ADMIN` 角色。用 admin 账号登录（`admin/Iam@2026`），不要用 alice。

### 9.5 SAML 启动报「no RelyingPartyRegistration」

`iam.saml.idp.metadata-url` 为空，已提供占位 registration，仅 WARN 不阻塞启动。真实接入填 metadata URL 即可。

### 9.6 LDAP 启动报「LDAP not configured」

`iam.ldap.url` 为空时禁用 LDAP 登录。租户级 LDAP 在 `tenants.ldap_url` 配置。

### 9.7 OAuth2 测试

```bash
# 授权码流程（浏览器打开）
http://localhost:8080/iam/oauth/authorize?response_type=code&client_id=demo-client&redirect_uri=http://localhost:5173/oauth2-callback&scope=openid

# 换 token
curl -X POST http://localhost:8080/iam/oauth/token \
  -d 'grant_type=authorization_code&code=<CODE>&redirect_uri=http://localhost:5173/oauth2-callback&client_id=demo-client&client_secret=demo-secret'

# introspect
curl -X POST http://localhost:8080/iam/oauth/introspect \
  -d 'client_id=demo-client&client_secret=demo-secret&token=<ACCESS_TOKEN>'
```

## 10. 升级与回滚

- 升级前：`./scripts/build.sh` 全量构建验证 + `cd backend && mvn test`。
- 数据库变更走 Liquibase changeset，不手改 schema。
- 回滚：Liquibase `rollback` + git checkout 旧代码 + 重启。
- 蓝绿：两套 docker-compose stack，切 nginx upstream。

## 11. Windows 开发实战

### 11.1 一键脚本命令速查

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned   # 仅首次
.\scripts\dev.ps1        # 启动 dev（redis + backend + vite）
.\scripts\start.ps1      # 生产 docker compose
.\scripts\stop.ps1       # 停止全部
.\scripts\build.ps1      # 仅构建，不启动
```

### 11.2 常见问题

**Q：`vite dev` 起不来？**
A：确认已 `cd frontend && npm i`。确认 Node.js ≥ 20（`node -v`）。

**Q：后端报 `H2 database not found`？**
A：删 `%USERPROFILE%\iam-dev\` 后重启，脚本会自动播种。

**Q：前端白屏，浏览器控制台报 `hasRole is not a function`？**
A：`npm run dev` 需要和 backend 同起（`~\scripts\dev.ps1`），否则 `/iam/admin/*` 反向代理找不到 admin 服务。

**Q：改了 `global.css`，浏览器没生效？**  
A：Ctrl+Shift+R 强刷。Vite HMR 会导入 CSS，但若 swiched 过 `.ps1` 脚本重启，需要清浏览器缓存。

### 11.3 前端设计系统速查

```
公共变量在 frontend/src/styles/global.css
├── --accent: #5b4dff        主按钮 / 焦点 / active
├── --accent-dim: #4a3fcf     hover
├── --bg-wash: #f6f8fb        admin 灰
├── --border: #e4e8ef
├── --shadow-sm|md|lg|xl     四档投影
└── --radius-sm|md|lg|xl|pill

公共组件：
├── PaneToolbar.vue   搜索 + 新建 + action 插槽（panes 共用）
├── StatCard.vue      KPI 数卡（DashboardView）
├── EmptyState.vue    空状态
└── CallbackView.vue  回调等待页（8 点圆环绕动 spinner）
```

## 12. 安全 checklist（上线前）

- [ ] `IAM_JWT_SECRET` 已改成 ≥32 字节随机值，两服务相同
- [ ] `DB_PASS` / `REDIS_PASS` 已改非默认
- [ ] `iam.cors.allowed-origins` 已改为生产域名
- [ ] 演示账号 `admin/Iam@2026` 密码已改或已禁用
- [ ] `DemoSeeder` 在生产 profile 下不执行（移除 `@Component` 或加 `@Profile("dev")`）
- [ ] actuator 除 health 外不对外暴露
- [ ] HTTPS 终端（nginx / 反向代理 TLS）
- [ ] 真实 SMS / 邮件通道已接入（替换 stub）
- [ ] 审计日志定期归档
