# IAM Spring Boot Starter

Spring Boot starter for external resource servers protected by IAM access tokens.

## Coordinates

```xml
<dependency>
  <groupId>com.iam</groupId>
  <artifactId>iam-sdk-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration

```yaml
iam:
  sdk:
    enabled: true
    issuer: iam-platform
    auth-server-base-url: http://localhost:8080/iam
    jwks-uri: http://localhost:8080/iam/oauth/jwks
    exclude-paths:
      - /actuator/health
```

## Auto-configured Beans

- `IamAuthClient`
- `IamJwtVerifier`
- `IamAuthenticationFilter`
- `IamSecurity`

The filter reads `Authorization: Bearer <token>`, verifies the JWT through JWKS, stores `IamPrincipal` in request attribute `iam.principal`, and sets Spring Security authentication.

## Controller Usage

```java
@RestController
class OrderController {
    private final IamSecurity iam;

    OrderController(IamSecurity iam) {
        this.iam = iam;
    }

    @PostMapping("/orders/approve")
    Map<String, Object> approve() {
        iam.requirePermission("order:approve");
        return Map.of("operator", iam.requireCurrent().getUsername());
    }
}
```

## Notes

- Missing token is not rejected by the filter; business code or Spring Security rules decide whether authentication is required.
- Invalid token returns `401 INVALID_TOKEN`.
- Permission checks use JWT `perms`.
- For ABAC/data authorization, use `IamAuthClient.checkPermission`.
