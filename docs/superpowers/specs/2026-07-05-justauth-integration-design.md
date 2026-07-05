# JustAuth Integration Design

## Context

The IAM project already has a social login surface:

- Backend endpoints:
  - `GET /api/auth/social/{provider}/authorize`
  - `GET /api/auth/social/{provider}/callback`
- Frontend social-login tab with providers:
  - `wechat`
  - `alipay`
  - `qq`
  - `dingtalk`
  - `wecom`
- Persistence and local IAM flow:
  - `SocialBindingEntity`
  - `UserEntity`
  - JWT issuing through `JwtTokenService`
  - audit logging through `AuditLogService`

The current `SocialLoginService` hand-rolls provider-specific OAuth calls. Several providers are partial or stub-like, and callback URLs are hard-coded to localhost. JustAuth should replace this provider OAuth layer while preserving the project-owned user binding and token issuance behavior.

## Goals

Integrate `me.zhyd.oauth:JustAuth` for the existing social providers:

- WeChat
- Alipay
- QQ
- DingTalk
- WeCom

Keep the implementation extensible so later providers can be added by registering provider metadata and configuration, without changing the controller, frontend callback flow, user binding, JWT issuing, or audit flow.

## Non-Goals

This change will not introduce a new social-provider admin UI. It will reuse the existing runtime configuration surface backed by the dynamic configuration table.

This change will not redesign token delivery. The existing callback behavior redirects the browser to the frontend with the access token in a query parameter. That behavior is preserved for compatibility, although an HttpOnly cookie flow would be safer as a separate hardening task.

This change will not replace local IAM users, social bindings, audit logging, or JWT issuing with JustAuth concepts. JustAuth is only the third-party authorization adapter.

## Architecture

`AuthController` remains the HTTP boundary. It will keep delegating social login to `SocialLoginService`.

`SocialLoginService` will become the application service for:

- generating third-party authorization URLs;
- handling callback codes;
- translating JustAuth `AuthUser` data into the local social identity model;
- finding or provisioning local users;
- issuing local IAM access tokens;
- recording audit logs.

A provider registry will isolate provider-specific metadata. The registry can be implemented as a small component or a focused private collaborator, depending on the final code shape. It will own:

- supported provider keys such as `wechat`, `qq`, `alipay`, `dingtalk`, `wecom`;
- the JustAuth source for providers supported by JustAuth defaults;
- any custom source required for providers that are not covered cleanly by JustAuth defaults;
- the config object needed to build an `AuthRequest`;
- redirect URI construction.

The service will depend on an `AuthRequest` factory instead of constructing provider-specific request details throughout the callback path. This keeps the core login flow stable and makes unit tests possible without real provider network calls.

## Configuration

Configuration keys remain under `iam.social`, but their persisted source of truth is the dynamic configuration table `system_config`.

`JustAuthSocialAuthGateway` reads values through `DynamicConfig` and falls back to Spring `Environment` only as a bootstrap/default fallback. This keeps the existing property names compatible while aligning runtime behavior with the global dynamic-config design.

Existing keys should be preserved where possible:

- `iam.social.wechat.app-id`
- `iam.social.wechat.app-secret`
- `iam.social.alipay.app-id`
- `iam.social.alipay.app-private-key`
- `iam.social.alipay.alipay-public-key`
- `iam.social.qq.app-id`
- `iam.social.qq.app-key`
- `iam.social.dingtalk.app-id`
- `iam.social.dingtalk.app-secret`
- `iam.social.wecom.corp-id`
- `iam.social.wecom.agent-id`
- `iam.social.wecom.corp-secret`

Add one base callback setting:

- `iam.social.redirect-base-url`

Default dynamic-config row:

| cfg_key | cfg_value | cfg_type |
| --- | --- | --- |
| `iam.social.redirect-base-url` | `http://localhost:8080/iam/api/auth/social` | `string` |

The provider callback URL is then:

```text
{redirect-base-url}/{provider}/callback
```

This removes hard-coded callback URLs from Java code while keeping local development behavior unchanged.

Alipay additionally supports `iam.social.alipay.redirect-uri` because Alipay validates that the callback URL matches the registered HTTPS URL.

Liquibase owns the table and default rows so a fresh checkout can start and run tests without manual database setup. Redis pub/sub is a best-effort cache invalidation path: if Redis is unavailable, the application still starts with DB-backed config and logs a warning.

## Provider Mapping

Initial provider keys:

| Local key | JustAuth source intent |
| --- | --- |
| `wechat` | WeChat open platform login |
| `qq` | QQ login |
| `alipay` | Alipay login |
| `dingtalk` | DingTalk login |
| `wecom` | WeCom / enterprise WeChat handling |

If a provider is not configured, the service returns the existing-style `SOCIAL_NOT_CONFIGURED` error before building a request.

If a provider key is unknown, the service returns `UNKNOWN_PROVIDER`.

## Data Flow

Authorization:

1. Frontend calls `GET /api/auth/social/{provider}/authorize`.
2. Controller delegates to `SocialLoginService.authorizeUrl(provider)`.
3. Service resolves the provider registry entry.
4. Service builds an `AuthRequest`.
5. Service returns `request.authorize(state)`.
6. Frontend redirects the browser to the returned URL.

Callback:

1. Provider redirects to `/api/auth/social/{provider}/callback?code=...`.
2. Controller delegates to `SocialLoginService.callback(provider, code, ip)`.
3. Service resolves provider and builds an `AuthCallback`.
4. Service calls JustAuth login.
5. Service validates the JustAuth response and extracts a stable provider user id from `AuthUser.uuid`.
6. Service looks up `SocialBindingEntity` by provider and provider user id.
7. If a binding exists, the linked local user is loaded.
8. If no binding exists, a local user is provisioned and a binding is created.
9. Service issues a local IAM access token through `JwtTokenService`.
10. Service writes a `SOCIAL_LOGIN` audit log.
11. Controller redirects the browser to the existing frontend social callback URL with the token.

## Extension Model

Adding a future provider should require:

1. Add configuration keys under `iam.social.{provider}`.
2. Add one provider registry entry mapping the local key to a JustAuth source and config.
3. Add or reveal a frontend button if the provider should be visible in the login page.
4. Add tests for the new mapping and missing-configuration behavior.

The controller and token issuance flow should not change for new providers.

## Error Handling

The service will keep the existing domain error style:

- `UNKNOWN_PROVIDER` for unsupported provider keys.
- `SOCIAL_NOT_CONFIGURED` for supported providers missing required config.
- `SOCIAL_FAIL` for failed JustAuth login, missing `uuid`, or invalid provider response.

Provider errors should not leak secrets or raw provider tokens into logs or response messages.

## Testing

Implementation should add focused backend tests before changing service behavior:

- provider registry maps each current provider key;
- unknown provider returns `UNKNOWN_PROVIDER`;
- missing provider config returns `SOCIAL_NOT_CONFIGURED`;
- successful callback with a fake JustAuth user provisions a local user and binding;
- successful callback for an existing binding reuses the existing local user;
- issued token uses the provider as the login channel;
- audit log is recorded on successful social login.

Real provider integration tests are out of scope because they require external credentials and callbacks.

## Dependency Scope

Add JustAuth to Maven dependencies in the module that owns `SocialLoginService`. Add the HTTP implementation dependency required by the selected JustAuth setup. Versions should be pinned in the parent dependency management if shared, or directly in `iam-common` if only used there.

JustAuth `1.16.7` brings `simple-http` transitively in this project, so no separate HTTP client dependency is required.

## Risks

WeCom support may require a custom JustAuth source or provider-specific adaptation if the default JustAuth source does not match the current WeCom flow. The registry boundary is designed so this can be handled locally without changing the rest of the login pipeline.

The existing token-in-query redirect is retained for compatibility but is less secure than an HttpOnly cookie exchange. This should be tracked separately if production hardening is required.
