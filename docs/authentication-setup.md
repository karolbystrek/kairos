# Self-hosted ZITADEL authentication setup

Run `./setup.sh`, then start Compose separately with `docker compose up --build`.
Setup creates webhook/push secrets, the stable 32-character `zitadel-masterkey`,
and optional local TLS material. No external provider account or SMTP setup is
required. Existing `.env` files kept with `--keep-env` do not gain new variables;
copy the ZITADEL variables from `.env.example` when retaining an old file.
Older four-file key sets need explicit `--replace-keys` to add the master key.
Setup preserves an existing ZITADEL master key even when replacing application
keys. Never discard that key while retaining the provider database.

## Compose startup

1. PostgreSQL starts with the existing Kairos database. ZITADEL initializes a
   separate database and user using the configured PostgreSQL bootstrap account.
   Configure `ZITADEL_DB`, `ZITADEL_DB_USER`, and `ZITADEL_DB_PASSWORD`.
2. ZITADEL v4.19.4 starts on the private data network as `http://zitadel:8080`.
   It exposes no host port. Its HTTP endpoint is used only inside this trusted
   private network; browser/API traffic remains behind the existing HTTPS gateway.
   A supplied provider Login UI is not needed for Kairos's custom login flow.
3. A one-shot Python bootstrap creates the service account `kairos-api`, grants
   standard `IAM_LOGIN_CLIENT` and organization `ORG_USER_MANAGER` roles, and
   writes a non-expiring PAT into the private `zitadel-api-credentials` volume.
   The API mounts this volume read-only and starts after bootstrap completes.
   The administrator PAT lives in a different private bootstrap volume and is
   never mounted into the API. No bootstrap credentials are printed.
4. The API loads `ZITADEL_TOKEN_LOCATION` and calls `ZITADEL_API_URL`.
   Service credentials remain private and connections use the trusted private
   network or HTTPS.

The shared provider organization holds identities only; restaurant permissions
remain in Kairos. Initial password policy requires 12 characters, without forced
character classes or periodic password changes. API/forms enforce the provider's
200-character email/password input bound and confirmation. If the operator changes the provider
policy, update the visible password guidance to match.

## Sessions and account flows

Users register and log in on the Kairos panel with email/password. Registration
immediately creates a tenant/admin or consumes a fixed manually shared member
invitation. Emails stay unverified; verification codes are discarded rather than
mailed. Email verification, password recovery, social login and MFA are deferred.
Signed-in users can change a password using the current password; this signs out
all sessions and requires a new login.

Spring Session JDBC stores sessions in the Kairos database. The host-only,
Secure, HttpOnly `__Host-session` cookie is an opaque ID. Protected requests
validate provider factors and current local permissions and renew 30-day idle
expiry and cookie retention. There is no absolute lifetime for active users.
Background authenticated requests also count as activity. Provider outages return
503 while retaining local sessions; invalid provider state ends the local session.

Only provider session/user IDs are stored in server-side session attributes. The
provider session bearer token is discarded; the creator service account can
retrieve/terminate its sessions using its own private service credential.
Protect the database and its backups as credential-bearing data. No browser JWT,
refresh endpoint, custom tab coordination or authentication response-body tokens
are used. Ordinary logout revokes the current browser; Sign out everywhere revokes
all Kairos sessions, even if provider cleanup fails.

## Operations and limits

Back up both databases, the master key and the private provider credential
volumes. Changing database passwords in `.env` alone does not rotate existing
PostgreSQL roles. The bootstrap PAT is an operator credential and needs restricted
storage; the API PAT has only the standard roles required by this integration.
New API PATs use ZITADEL's no-expiry value `9999-12-31T23:59:59Z`; the pinned
v4.19.4 v2 API requires the expiration field. Existing tokens retain their configured expiration;
bootstrap never replaces an existing token. To rotate a token, provision a
replacement with the provider's no-expiry value through trusted provider
administration, atomically
replace `kairos.pat` in its credential volume, restart the API, then revoke the old
PAT. Keep this operational process
separate from application account/session expiry.

No existing account/password migration is needed: V1 is the initial development
schema. An existing development database may have
a V1 checksum mismatch; reset it yourself when desired. Implementation never
resets user-owned containers or volumes automatically.

Provider provisioning and Kairos transactions cannot be atomic across services.
On registration failure, Kairos attempts cleanup of the newly created identity.
If provider cleanup fails or a create response is lost, an orphan may require
operator deletion before retrying registration. No distributed transaction or
background repair queue is introduced. Expired/local-only-revoked provider
sessions may remain in ZITADEL, but cannot authorize Kairos without a valid local
session; provider housekeeping can be added if retained session volume warrants it.

## Provider verification

Automated tests isolate the provider and verify REST payloads, factor validation,
JDBC expiry/revocation, cookies, CSRF and failure behavior. Separately verify the
pinned provider's unverified-email login, disabling/deleting a user, password
changes, explicit session termination and outages. Source/test checks do not
exercise the running Compose stack.

References: [Session API](https://zitadel.com/docs/guides/integrate/login-ui/username-password),
[session validation](https://zitadel.com/docs/guides/integrate/login-ui/session-validation),
and [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
