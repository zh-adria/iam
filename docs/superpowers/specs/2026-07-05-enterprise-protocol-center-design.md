# Enterprise Protocol Center Design

Date: 2026-07-05
Status: Approved for implementation planning

## Goal

Build the next IAM phase as an Enterprise Protocol Center. The phase covers four protocol areas in one product design:

- OIDC Provider completion
- SAML 2.0 completion
- LDAP / Active Directory configuration and diagnostics
- SCIM 2.0 provisioning entry

Implementation must be split into four small tasks or PRs, one per protocol area. Each task must be independently testable and must not require WebAuthn, RADIUS, Kerberos/SPNEGO, OAuth1, WS-Federation, or more social providers.

## Current Context

The project already has working or partial protocol surfaces:

- OAuth2 authorization code, PKCE, token, introspect, revoke, OIDC discovery, JWKS, ID Token, and userinfo.
- SAML IdP registration entities and a Spring Security SAML2 login flow.
- LDAP bind login, per-tenant URL/base hints, LDAP config helpers, and group-to-role mapping work.
- SCIM controller, SCIM token service, SCIM user/group entities, and admin protocol UI work in progress.

The next phase should productize and verify these capabilities rather than adding unrelated protocols.

## Architecture

The admin service owns protocol configuration and diagnostics.

- `iam-admin` exposes management APIs for configuration, metadata refresh, connection tests, SCIM token lifecycle, status checks, and protocol summaries.
- `iam-auth-server` exposes runtime protocol endpoints: OIDC discovery/JWKS/userinfo/token, SAML SSO/ACS/SP metadata, LDAP login, and SCIM `/scim/v2/*`.
- `iam-common` continues to hold shared entities, repositories, services, JWT, dynamic config, LDAP, SAML registration, and SCIM token logic.
- The frontend protocol center remains in `AuthProtocolsPane.vue`, organized by protocol tabs.

Every protocol tab should show four kinds of information:

- Configuration
- Status
- Test or debug action
- Setup documentation hints

This phase intentionally avoids a full authorization server rewrite. The existing OAuth2/OIDC implementation remains the base unless a narrow correctness fix is required.

## OIDC Provider

### Backend

OIDC must become an explicit provider surface, not only an OAuth2 side effect.

- Discovery must return an absolute `issuer` and absolute endpoint URLs.
- Issuer resolution must prefer explicit configuration; if absent, build from the incoming request and context path.
- JWKS must return the active RS256 public key with stable `kid`.
- Token exchange must issue `id_token` when granted scopes include `openid`.
- ID Token claims must include `sub`, `aud`, `iss`, `exp`, `iat`, and may include `nonce`, `email`, `preferred_username`, and `tenant`.
- UserInfo must return claims according to granted scopes:
  - `openid`: `sub`
  - `profile`: `preferred_username`
  - `email`: `email`
  - `phone`: `phone_number`
  - project extension: `tenant`
- OAuth2 client configuration remains the source for scopes, token TTLs, auto approve, and ID Token extra claims JSON.

### Admin UI

The OIDC tab must show:

- Issuer
- Discovery URL
- JWKS URL
- UserInfo URL
- Provider check action
- Link or compact summary for OAuth2 client scopes and ID Token claim configuration

The provider check must validate:

- Issuer is absolute.
- Discovery contains required OIDC fields.
- JWKS returns at least one key.
- Demo or selected client includes `openid`.

### Tests

Tests must cover:

- Absolute discovery issuer and endpoint URLs.
- Non-empty JWKS with `kid`.
- Authorization code exchange with `openid` returns ID Token.
- ID Token contains expected issuer, audience, subject, and nonce.
- UserInfo filters claims by scope.
- Existing OAuth2 authorization code, PKCE, refresh token, and client credentials flows still pass.

## SAML 2.0

### Backend

SAML must use the database IdP registration table as the primary source. Static yml remains only as backward-compatible fallback.

Required behavior:

- Enabled DB registrations are loaded into the runtime relying party repository.
- Disabled registrations are not available for login.
- Admin can save metadata URL, raw metadata XML, or manual IdP entity/SSO fields.
- Metadata URL refresh downloads metadata, updates stored XML, records `metadataLastRefreshedAt`, and refreshes the runtime repository.
- Failed refresh returns a clear error and does not corrupt the last known working registration.
- SP metadata download returns XML with at least SP entity ID and ACS location.
- NameID format is stored and applied when supported by the builder.
- Attribute mapping JSON maps IdP attributes into IAM fields such as `username`, `email`, and `displayName`.

Certificate fields are configuration fields in this phase:

- `signingCertPem`
- `encryptionCertPem`

The phase may emit these in SP metadata when supported, but it does not implement full certificate lifecycle, automatic rotation, or complex SLO behavior.

### Admin UI

The SAML tab must support:

- List, create, edit, enable, disable, and delete IdP registrations.
- Import or refresh IdP metadata.
- Download SP metadata.
- View login URL and SP metadata URL.
- Show status: enabled, configured, metadata availability, last refresh time, and errors.

SAML form fields:

- Tenant code
- Registration ID
- IdP entity ID
- IdP SSO URL
- IdP metadata URL
- Raw IdP metadata XML
- SP entity ID
- ACS template
- Enabled flag
- Signing certificate PEM
- Encryption certificate PEM
- NameID format
- Attribute mapping JSON

### Tests

Tests must cover:

- Enabled-only runtime registration loading.
- Disabling a registration removes it from runtime lookup.
- Metadata refresh success updates XML and refresh timestamp.
- Metadata refresh failure preserves old metadata.
- SP metadata contains entity ID and ACS URL.
- Attribute mapping resolves `username`, `email`, and `displayName`.

## LDAP / Active Directory

### Backend

LDAP remains an authentication and provisioning entry, not a full directory synchronization engine.

Configuration levels:

- Global config:
  - `iam.ldap.url`
  - `iam.ldap.base`
  - `iam.ldap.user-dn-pattern`
  - `iam.ldap.user-search-filter`
  - `iam.ldap.manager-dn`
  - `iam.ldap.manager-password`
  - `iam.ldap.attribute-mapping`
- Tenant override:
  - `TenantEntity.ldapUrl`
  - `TenantEntity.ldapBase`

Tenant-specific URL/base must be used consistently by login, connection testing, attribute fetching, and group-to-role sync.

Connection testing must return structured results:

- Connection success
- Bind success
- Search success
- Search hit count
- Sample mapped attributes
- Error code and message on failure

Supported test modes:

- Anonymous bind
- Manager bind
- Search by configured or supplied user search filter

Templates must be available for:

- OpenLDAP
- Active Directory UPN
- Active Directory DN

LDAP user input in filters must be safely encoded or built through Spring LDAP filter APIs.

Attribute mapping must support at least:

- `email`
- `displayName`

Additional fields such as `department` and `title` can be shown in test output but are not required to be persisted in this phase.

### Admin UI

The LDAP tab must support:

- Template selector.
- Global config form.
- Tenant override clarity.
- Connection test form.
- Structured test result display.
- LDAP group DN to IAM role mapping.

Templates:

- OpenLDAP:
  - user DN pattern: `uid={0},ou=people`
  - search filter: `(uid={0})`
  - mapping: `mail=email,cn=displayName`
- Active Directory UPN:
  - user DN pattern: `{0}@corp.local`
  - search filter: `(sAMAccountName={0})`
  - mapping: `mail=email,displayName=displayName`
- Active Directory DN:
  - user DN pattern: `CN={0},OU=Users,DC=corp,DC=local`
  - search filter: `(sAMAccountName={0})`
  - mapping: `mail=email,displayName=displayName`

### Tests

Tests must cover:

- Successful embedded LDAP login.
- Bad password.
- Attribute mapping during provisioning.
- Tenant LDAP URL/base taking precedence over global config.
- Connection test missing config, bind failure, and search miss.
- Group-to-role sync using the same tenant LDAP config as login.
- Special characters in username do not break LDAP filter construction.

## SCIM 2.0

### Backend

SCIM must become a formal provisioning entry for users and groups.

Read-only metadata endpoints may remain public or low-risk:

- `/scim/v2/ServiceProviderConfig`
- `/scim/v2/ResourceTypes`
- `/scim/v2/Schemas`

User and group provisioning endpoints must require admin or SCIM provisioner authentication:

- `/scim/v2/Users`
- `/scim/v2/Groups`

SCIM token behavior:

- Created by admin.
- Raw token is shown once.
- Database stores bcrypt hash, prefix, tenant code, scope, enabled flag, expiry, creation time, and last-used time.
- Revoked or expired tokens are rejected.
- Token tenant code limits provisioning to that tenant.

Users baseline:

- `GET /Users` with pagination.
- Simple filters:
  - `userName eq "value"`
  - `emails.value eq "value"`
  - `active eq true|false`
- `GET /Users/{id}`.
- `POST /Users` creates a user from SCIM shape.
- `PATCH /Users/{id}` supports add/replace for `userName`, `active`, `emails`, and `phoneNumbers`.
- `DELETE /Users/{id}` soft-disables the user with `status=0`.

Groups baseline:

- `GET /Groups` with pagination and simple `displayName eq` filter.
- `GET /Groups/{id}`.
- `POST /Groups`.
- `PATCH /Groups/{id}` supports add, replace, and remove for members.
- `DELETE /Groups/{id}` deletes the SCIM group and member rows.
- Members are persisted in `auth_scim_group_member`.

Out of scope:

- Bulk
- ETag/versioning
- Full SCIM filter grammar
- Password synchronization

### Admin UI

The SCIM tab must show:

- Base URL
- ServiceProviderConfig URL
- Token list
- Create token action
- Revoke token action
- One-time raw token display
- Debug panel for list users, create test user, and list groups
- Okta/Azure AD setup hints: base URL and bearer token

### Tests

Tests must cover:

- Missing token cannot write users or groups.
- Expired or revoked token is rejected.
- Token tenant cannot write another tenant's resources.
- User create parses SCIM `emails` and `phoneNumbers` arrays correctly.
- User patch updates username, active, email, and phone.
- User delete soft-disables.
- Group create, patch members, and delete maintain member rows correctly.
- ListResponse includes `schemas`, `totalResults`, `startIndex`, `itemsPerPage`, and `Resources`.

## Data Flow

OIDC runtime:

1. Client redirects user to `/oauth/authorize`.
2. IAM authenticates user.
3. Authorization code is issued with scopes, nonce, claims, and PKCE data.
4. Client exchanges code at `/oauth/token`.
5. Access token, refresh token, and optional ID Token are returned.
6. Client calls `/oauth/userinfo` with bearer access token.

SAML runtime:

1. Admin configures IdP.
2. Runtime registration repository loads enabled IdPs.
3. User starts `/saml2/authenticate/{registrationId}`.
4. IdP posts assertion to ACS.
5. IAM maps NameID and attributes.
6. IAM finds or provisions local user.
7. IAM issues local JWT and redirects to frontend.

LDAP runtime:

1. User submits username, password, and tenant code.
2. IAM resolves tenant LDAP config, falling back to global config.
3. IAM builds safe user filter or DN pattern.
4. LDAP bind authenticates credentials.
5. IAM fetches mapped attributes.
6. IAM finds or provisions local user.
7. IAM syncs configured LDAP group-to-role mappings.
8. IAM issues local JWT.

SCIM runtime:

1. Admin creates SCIM token and gives raw token to external IdP.
2. External IdP calls `/scim/v2/Users` or `/scim/v2/Groups`.
3. SCIM filter validates bearer token, expiry, enabled status, and tenant.
4. Controller applies provisioning operation within token tenant.
5. Response uses SCIM JSON shapes and errors.

## Error Handling

Errors must be structured and useful to administrators.

OIDC:

- Discovery/JWKS are public and should not leak secrets.
- Token errors remain OAuth-style JSON where existing behavior already does this.
- Provider check returns admin-facing validation details.

SAML:

- Metadata refresh failure returns a clear message.
- Invalid registration returns 404 for runtime metadata/login lookup.
- Disabled IdP must behave as unavailable.

LDAP:

- Missing URL/base returns `LDAP_CONFIG_MISSING`.
- Bind failure returns `LDAP_BIND_FAILED`.
- Search miss returns `LDAP_USER_NOT_FOUND`.
- Search or connection exceptions return `LDAP_CONNECTION_ERROR` with sanitized message.

SCIM:

- SCIM endpoints return SCIM error shape for protocol errors.
- Auth failures return 401/403.
- Missing resource returns `noSuchObject`.
- Invalid PATCH returns `invalidSyntax`.

## Implementation Split

Implement as four independent tasks:

1. OIDC Provider completion
   - Discovery, issuer, JWKS, userinfo scope filtering, ID Token checks, admin provider check.
2. SAML 2.0 completion
   - Metadata import/refresh, SP metadata, enabled status, attribute mapping, runtime refresh.
3. LDAP / AD diagnostics
   - Connection test, templates, tenant consistency, safe filters, group role mapping fix.
4. SCIM 2.0 provisioning entry
   - Token auth hardening, tenant boundary, Users/Groups baseline, admin debug panel.

Each task must include tests and documentation updates. A task is complete only when its protocol-specific tests and relevant existing regression tests pass.

## Documentation Updates

Update these docs during implementation:

- `docs/协议手册.md`: endpoint details, examples, known limits.
- `docs/产品需求手册.md`: phase scope and protocol maturity matrix.
- `docs/技术手册.md`: architecture and data flow details.
- README protocol status table if behavior changes.

## Acceptance Criteria

The phase is complete when:

- The admin Protocol Center shows OIDC, SAML, LDAP, and SCIM as configurable protocol modules.
- OIDC provider check passes in dev.
- SAML IdP registration can be enabled, disabled, refreshed, and produce SP metadata.
- LDAP connection test reports structured success/failure and tenant LDAP login works.
- SCIM token can provision users and groups within its tenant only.
- Four implementation tasks are independently reviewable.
- Protocol docs match implemented behavior.
- Existing auth/OAuth2/LDAP/SCIM tests pass, with new tests for the behavior listed in this spec.

