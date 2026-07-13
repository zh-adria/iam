# IAM Resource Server Demo

Example external Spring Boot resource server protected by IAM SDK.

## What It Shows

- Use `iam-sdk-spring-boot-starter`.
- Verify `Authorization: Bearer <access_token>` through IAM JWKS.
- Read current `IamPrincipal`.
- Protect business endpoints with permission codes.

## Configuration

Default config in `src/main/resources/application.yml`:

```yaml
server:
  port: 8090

iam:
  sdk:
    issuer: iam-platform
    auth-server-base-url: http://localhost:8080/iam
    jwks-uri: http://localhost:8080/iam/oauth/jwks
```

## Run

Start IAM auth-server first on `http://localhost:8080/iam`.

Then run:

```bash
mvn -pl iam-resource-server-demo spring-boot:run
```

## Get Token

```bash
curl -X POST http://localhost:8080/iam/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=demo-client" \
  -d "client_secret=demo-secret" \
  -d "scope=iam:menu:dashboard iam:role:create"
```

## Call Demo APIs

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8090/api/orders
```

Requires:

```text
iam:menu:dashboard
```

```bash
curl -X POST -H "Authorization: Bearer <access_token>" \
  http://localhost:8090/api/orders/approve
```

Requires:

```text
iam:role:create
```
