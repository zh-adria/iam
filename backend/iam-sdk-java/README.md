# IAM Java SDK

Lightweight Java SDK for IAM OAuth2/OIDC clients and resource servers.

## Coordinates

```xml
<dependency>
  <groupId>com.iam</groupId>
  <artifactId>iam-sdk-java</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Features

- OAuth2 token calls: authorization code, client credentials, refresh.
- Token introspection and revocation.
- JWKS fetch/cache and RS256 access token verification.
- Principal model: `IamPrincipal`.
- Permission helpers: `IamAuthorizer`.
- Central authorization check: `IamAuthClient.checkPermission`.

## Client Credentials

```java
IamAuthClient client = new IamAuthClient("http://localhost:8080/iam");

IamTokenResponse token = client.clientCredentials(
    "demo-client",
    "demo-secret",
    "iam:role:create"
);
```

## Verify Access Token

```java
IamJwtVerifier verifier = new IamJwtVerifier(
    "iam-platform",
    "http://localhost:8080/iam/oauth/jwks"
);

IamPrincipal principal = verifier.verify(token.getAccessToken());
IamAuthorizer.requirePermission(principal, "iam:role:create");
```

## Central Authorization Check

Use this when ABAC/data rules should stay in IAM.

```java
IamAuthorizationCheckResponse check = client.checkPermission(
    token.getAccessToken(),
    "iam:role:create",
    Map.of("tenantCode", "default")
);

if (!check.isAllowed()) {
    throw new SecurityException("Forbidden");
}
```

## Boundary

This SDK does not copy IAM policy storage or ABAC SpEL logic. Local checks use JWT `roles` and `perms`; centralized checks call IAM.
