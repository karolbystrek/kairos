# Provider setup and registration consistency

## Contents

- [Provider setup and registration consistency](#provider-setup-and-registration-consistency)
- [Contents](#contents)
- [4.1.1 Provider setup and registration consistency](#411-provider-setup-and-registration-consistency)

### 4.1.1 Provider setup and registration consistency

Compose pins ZITADEL v4.19.4, uses its own database/user on the existing
PostgreSQL server, and keeps the provider on the private data network without a
public port. A separate one-shot bootstrap provisions a Kairos service account
with the standard `IAM_LOGIN_CLIENT` and organization `ORG_USER_MANAGER` roles.
The bootstrap administrator PAT is not mounted into the API; only the service
PAT is mounted read-only. These provider roles are infrastructure permissions,
not restaurant roles. Kairos restaurant tenants do not become provider orgs.
The provider's supplied Login UI is unnecessary for this custom Session API flow.
Start with one instance and accept planned maintenance outages; HA is deferred.

`setup.sh` prepares the five application secrets including a stable 32-character
ZITADEL master key, preserving that key when replacing webhook/push material.
The provider needs the same master key for as long as its database is retained.
New API PATs use ZITADEL's no-expiry sentinel `9999-12-31T23:59:59Z`; the pinned
v4.19.4 v2 API requires an explicit expiration timestamp. Operators can revoke
or rotate them through provider administration. Bootstrap preserves existing PATs and does not change their
expiration. Provider data, master key and private credential volumes need backups and restricted access.
No SMTP provider is configured in this increment.

Validate invitation/email availability before provider provisioning. Create and
password-authenticate the provider identity, then commit the tenant/admin or
invitation/member transaction locally. First-location creation is a separate
authenticated transaction after account registration. Recheck invitation eligibility and consume
it atomically with the Account and assignment. On local failure, terminate the
new provider session and attempt deletion of only the newly provisioned identity.
No distributed transaction, queue or retry worker is introduced. A provider
outage during cleanup can leave an orphan identity requiring operator cleanup;
never attach an existing identity solely because its email matches.

Treat Kairos as a fresh development repository until the user changes that
policy: no existing-account/password migration or backward compatibility.
V1 contains the accepted initial schema, including standard Spring Session JDBC
tables. Do not introduce transitional migrations solely for development data.
Local resets remain user-owned actions and are not performed automatically.
See [authentication setup](../authentication-setup.md) for operational steps.

```mermaid
sequenceDiagram
    participant Panel as Kairos panel
    participant API as Spring API
    participant Provider as ZITADEL
    participant DB as PostgreSQL
    Panel->>API: Email/password + CSRF
    API->>Provider: Create session with user/password checks
    API->>Provider: Read session and verify factors
    API->>DB: Resolve eligible Account; save JDBC session
    API-->>Panel: Current Account + HttpOnly session cookie
    Panel->>API: Protected request + session cookie
    API->>Provider: Validate provider session
    API->>DB: Check current eligibility/permissions
    API->>DB: Execute authorized operation; renew idle expiry
    API-->>Panel: Response + renewed cookie retention
```

Validate unverified-email password login and provider disable/delete/password-
change/session-termination semantics against the pinned provider release. HTTP contract and provider-isolated tests do not replace this
live acceptance. Primary contracts: [Session API](https://zitadel.com/docs/guides/integrate/login-ui/username-password),
[session validation](https://zitadel.com/docs/guides/integrate/login-ui/session-validation),
and [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
