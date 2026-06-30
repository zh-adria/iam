# ponytail-debt: 已知简化与升级路径

| 项 | 当前 | 升级路径 |
|----|------|----------|
| 单 Maven 模块 | 4 个包代替 Cola 多模块 pom | 团队规模 > 5 人时拆 `iam-adapter/app/domain/infrastructure` 子模块 |
| 实体直落基础设施 | 跳过 Cola 的 Domain → DO 转换 | 高性能场景补 `Assembler` 做对象映射 |
| JWT HS256 | 单机对称密钥 | 多资源服务器场景切 RS256，密钥走 KMS |
| OAuth2 端点 | 仅客户端 CRUD | 引入 `spring-security-oauth2-authorization-server` 补 `/oauth/authorize` `/token` `/userinfo` |
| SAML/Kerberos/WebAuthn | 仅建模 | 接 `Spring Security SAML 2` + `WebAuthn4J` |
| LDAP | 字段已建 | 引入 `spring-ldap-ldif-core` + `LdapTemplate.search()` |
| 多租户隔离 | SHARED 模式 | Schema-per-tenant 用 Hibernate `MultiTenantConnectionProvider` |
| ABAC SpEL | 字段已存 | 在 SecurityConfig 用 `MethodSecurityExpressionHandler` 注入自定义 root |
| 社交登录 | 占位按钮 | 接 `OAuth2ClientFilter`，自定义 `CustomOAuth2UserService` |
| Session Cookie | 仅 JWT | 需要时引入 Spring Session + Redis |
