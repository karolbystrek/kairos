# Staff credentials and registration consistency

### Staff credentials and registration consistency

#### Credential storage

The API stores staff identities in the `staff_identities` table: an opaque
subject, the normalized email, a PBKDF2 password hash (Spring Security's
`Pbkdf2PasswordEncoder` defaults, no extra dependency and no 72-byte limit, so
the 200-character password bound works), the email-verified flag and the
current verification code as a SHA-256 hash with its one-hour expiry. Accounts
reference the identity by `provider_subject`. Sign-in with an unknown email
still checks a password hash, so it takes as long as a wrong password.

Like the Spring Session tables, `staff_identities` is authentication
infrastructure read before any RLS scope exists. It has no RLS policies; the
runtime role holds explicit SELECT/INSERT/UPDATE/DELETE grants and the database
isolation check excludes it. Protect the database and its backups as
credential-bearing data. Kairos sends verification codes through its own
transactional email worker; there is no SMTP or external identity service.

#### Registration consistency

Validate invitation/email availability before creating an identity.
Administrator registration commits the tenant/admin only after email
confirmation (or automatic verification with `EMAIL_VERIFICATION_ENABLED=false`
for local development); invitation registration commits its member transaction
after invitation validation and marks the invited email verified without a
separate code step. First-location creation is a separate authenticated
transaction after account registration. Recheck invitation eligibility and
consume it atomically with the Account and assignment.

The identity is written in the same PostgreSQL transaction as the registration
step, so a failed step leaves no orphan identity. An abandoned unverified
identity without an Account may be replaced by a new registration after the
resend cooldown, and the retention purge deletes it with its pending
registration. Never attach an existing identity solely because its email
matches.

#### Login and protected requests

```mermaid
sequenceDiagram
    participant Panel as Kairos panel
    participant API as Spring API
    participant DB as PostgreSQL
    Panel->>API: Email/password + CSRF
    API->>DB: Verify password hash; resolve eligible Account; save JDBC session
    API-->>Panel: Current Account + HttpOnly session cookie
    Panel->>API: Protected request + session cookie
    API->>DB: Check session cutoff, current eligibility/permissions
    API->>DB: Execute authorized operation; renew idle expiry
    API-->>Panel: Response + renewed cookie retention
```

See [maintenance](../deployment/MAINTENANCE.md) for credential operations.
Primary contract:
[Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
