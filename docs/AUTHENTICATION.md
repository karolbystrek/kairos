# Kairos Staff Authentication

Status: first local-authentication API and panel integration implemented.

This document refines the staff-authentication requirements in
[`REQUIREMENTS.md`](REQUIREMENTS.md). `REQUIREMENTS.md` remains the source of
truth for system-wide behavior and architecture. Accepted authentication
decisions and implementation details must be kept synchronized between both
documents.

## 1. Scope and Ownership

Kairos implements and operates its staff authentication with Spring Security.
The backend owns local credentials, login flows, browser sessions, account
linking, logout, session revocation, and authorization context. Established
framework and protocol implementations must be used; Kairos must not implement
password hashing, cryptographic signing, CSRF primitives, or OAuth2/OIDC
protocols from scratch.

The first authentication increment includes:

* provisioned internal accounts with no public self-registration;
* normalized username and BCrypt password login;
* short-lived signed access JWTs and rotating refresh credentials;
* secure cookie transport and CSRF protection;
* logout, session revocation, and current-account retrieval;
* tenant, role, and location authorization for staff operations;
* trusted staff identity in audit history;
* integration tests for authentication and authorization boundaries.

OAuth2/OIDC login is explicitly outside the first authentication increment. It
remains a required later capability, initially demonstrated with Google. The
first increment must therefore leave the database model, account domain,
principal construction, session issuance, and service boundaries ready for a
second login method without implementing OIDC endpoints or callbacks yet.

Passkeys, MFA, self-service password recovery, device-activation codes, and
enterprise SSO are also not part of the first increment. They may be added later
without changing the internal account and authorization model.

Anonymous customer tracking and POS API-key authentication remain separate from
staff authentication.

## 2. Goals

Kairos authentication should:

* make frequent sign-in on restaurant panel devices predictable;
* give administrators and managers personal accounts rather than shared
  credentials;
* make every device-oriented operator account independently revocable;
* keep access and refresh credentials out of frontend JavaScript and browser
  storage;
* establish one internal account identity regardless of login method;
* derive tenant, location, role, and audit identity on the backend;
* reject disabled accounts and suspended assignments consistently;
* provide safe refresh rotation, logout, and credential-reuse handling;
* expose JSON/Problem Details failures suitable for the Next.js panel.

## 3. Account and Authorization Model

Every successful login resolves to exactly one active Kairos `accounts` row.
That row belongs directly to one tenant and is the stable identity used by
authorization and auditing.

### 3.1 Tenant administrator

A tenant administrator:

* has `tenant_role = ADMIN`;
* has tenant-wide access and no location assignment;
* may view and manage all tenant locations;
* may provision managers and operators within the tenant;
* should use a personal account, preferably with OIDC when configured.

### 3.2 Location manager

A location manager:

* has `tenant_role = MEMBER`;
* has one active `MANAGER` location assignment;
* may access only that location;
* may provision operators only for that location;
* should use a personal account.

### 3.3 Location operator

A location operator:

* has `tenant_role = MEMBER`;
* has one active `OPERATOR` location assignment;
* may access and manage orders only for that location;
* cannot provision accounts;
* is normally device-oriented, with a separate account for every independently
  revocable panel device.

An account with `tenant_role = MEMBER` must have one active assignment before it
can perform staff operations. An administrator must not receive a location
assignment. These invariants are enforced in application services and covered
by integration tests.

## 4. Authentication Flows

### 4.1 Local login

1. The panel obtains a CSRF token.
2. The user submits a normalized username and password over HTTPS.
3. The backend loads the account by normalized username.
4. Spring Security verifies the BCrypt hash using a configured adaptive work
   factor.
5. The backend rejects missing, disabled, or otherwise ineligible accounts with
   the same public authentication failure.
6. The backend creates a refresh session and issues the access and refresh
   cookies.
7. Authentication success and failure are recorded without logging credentials
   or tokens.

Login responses must not reveal whether a username exists, an account is
disabled, or a password was incorrect. Timing differences should be bounded by
performing an equivalent password-hash verification for unknown usernames. The
fallback candidate is generated randomly and hashed once at application startup;
no dummy credential is embedded in source code or generated per request.

### 4.2 Future OAuth2/OIDC login

This subsection is a forward-design constraint, not part of the first
authentication implementation scope.

Spring Security uses the Authorization Code flow for the configured provider.
The provider callback must resolve an immutable `(provider, subject)` identity
already linked to a provisioned Kairos account.

Provider email alone must never grant tenant access, silently create an account,
or choose a tenant. Email is mutable and is profile information rather than the
authorization identity.

Recommended linking flow:

1. an authorized administrator or manager provisions the target Kairos account;
2. the target user authenticates locally or follows a future single-use setup
   flow;
3. Kairos starts the provider-linking authorization request;
4. the callback validates state, nonce, issuer, audience, and authorization
   response through Spring Security;
5. Kairos stores the provider and immutable subject against the account;
6. subsequent OIDC logins issue the same Kairos access and refresh credentials
   as local login.

The database must prevent one external identity from being linked to multiple
accounts. Linking and unlinking are security-sensitive actions and require
explicit authorization and audit logging.

To avoid coupling the first implementation to password login, it must:

* model the authenticated principal around the Kairos account ID rather than a
  username or login method;
* keep password verification behind a local-authentication service boundary;
* keep successful authentication separate from access/refresh session issuance;
* allow the account model and schema to represent a future account without a
  local password;
* keep external identities in a separate persistence concept rather than adding
  provider columns to `accounts`;
* avoid placing login-method-specific data in authorization services or access
  JWT claims.

### 4.3 Refresh

The refresh credential is an opaque, cryptographically random value. Only a
cryptographic hash is stored in PostgreSQL.

On refresh, the backend:

1. requires a valid CSRF token;
2. hashes the presented refresh credential and locks the matching session row;
3. rejects expired, revoked, replaced, or unknown credentials;
4. reloads the account and rejects disabled accounts;
5. validates the account's current authorization eligibility;
6. creates a replacement session in the same token family;
7. marks the consumed session as revoked and links it to the replacement;
8. commits the rotation atomically;
9. issues a new access JWT and refresh cookie.

Reuse of a consumed refresh credential indicates theft or an unsafe replay. The
backend revokes the entire token family. The implementation must define and test
the handling of legitimate concurrent refresh attempts from multiple browser
tabs so that normal concurrency does not silently weaken reuse detection.

### 4.4 Logout and revocation

`POST /api/auth/logout` revokes the current refresh session and clears both
authentication cookies. `POST /api/auth/logout-all` revokes every refresh
session owned by the current account and clears the current cookies.

Disabling an account prevents new login and refresh immediately. Already-issued
access JWTs remain usable only until their short expiry unless a high-risk
operation performs an additional current-account check.

### 4.5 Current account

`GET /api/auth/me` returns the current authorization context needed by the
panel:

* account ID and display name;
* tenant ID and tenant role;
* location assignment ID, name, and assignment role when applicable;
* capabilities that are useful for presentation, while the backend still
  authorizes every operation independently.

It must not expose password hashes, refresh-session hashes, provider tokens, or
unnecessary personal information.

## 5. Access JWT and Refresh Session

The first increment uses the following initial policy:

* access JWT lifetime: 5 minutes;
* access JWT algorithm: RS256 with a configured RSA key pair;
* access cookie: `__Host-access-token`;
* refresh cookie: `__Host-refresh-token`;
* cookie and CSRF-header names are a fixed HTTP contract rather than
  environment-specific configuration;
* cookie attributes: `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`, with no
  `Domain` attribute;
* refresh idle lifetime: 7 days;
* refresh token-family absolute lifetime: 30 days;
* personal and device-oriented accounts use the same refresh policy until a
  separate account-kind distinction is explicitly introduced;
* no authentication credential in `localStorage` or `sessionStorage`.

The access JWT contains only stable security inputs and protocol metadata:

* issuer and audience;
* subject containing the Kairos account ID;
* tenant ID;
* tenant-level role;
* issued-at and expiry times;
* unique token ID.

The JWT must not contain the current location assignment, display name, email,
or other mutable profile data. The backend resolves the current assignment from
PostgreSQL.

Runtime signing keys and secrets are supplied through external configuration
and must not be committed to the repository. The Spring application never
generates a signing key and fails to start unless both key resource locations
are configured. Production requires a documented signing key rotation
procedure.

The API accepts X.509 public-key and PKCS#8 private-key resource locations.
Production must configure both locations from externally managed secrets. Local
Compose runs an idempotent initialization service that generates a 3072-bit RSA
key pair only when the `jwt_signing_keys` named volume is empty. The API mounts
that volume read-only and therefore reuses the same key across container
restarts and rebuilds. `docker compose down` preserves the volume and keys;
explicitly deleting Compose volumes also deletes the local key pair and causes
the initializer to create a new one on the next startup.

The initial production rotation procedure is a coordinated cutover:

1. generate and securely distribute a new RSA key pair;
2. update every API instance's external key resources and restart the instances;
3. allow clients holding an old access JWT to receive `401` and recover through
   the refresh flow, which does not depend on the access JWT;
4. verify new JWT issuance and validation, then retire the old private key.

Because access JWTs live for only five minutes, this cutover has a bounded
impact. A future multi-key decoder with explicit key IDs is required before a
zero-interruption overlapping-key rotation is promised.

The first tenant, location, and administrator are bootstrapped out of band with
manual SQL. The administrator row contains a normalized username and a BCrypt
hash produced by trusted tooling, has `tenant_role = ADMIN` and `status =
ACTIVE`, and has no location assignment. Production migrations remain empty of
environment-specific tenants, locations, accounts, or credentials, and no API
endpoint can create another tenant administrator in this increment.

## 6. CSRF and Browser Request Behavior

Cookie-authenticated unsafe requests require CSRF protection, including login,
refresh, logout, account linking, account provisioning, and order mutations.

The panel obtains a readable CSRF token from the API's secure, host-only
`__Host-XSRF-TOKEN` cookie and copies it into the configured `X-XSRF-TOKEN`
request header. The backend accepts the token only through that header, never
through a form parameter. Authentication cookies remain `HttpOnly` and are
never read by the panel.

Spring Security's SPA-specific CSRF behavior must be handled deliberately:

* issue a token when the panel bootstraps authentication;
* rotate or reissue it after login and logout when Spring clears the previous
  token;
* return a distinct Problem Details response for missing or invalid CSRF;
* never disable CSRF for cookie-authenticated staff endpoints.

An expired or missing access JWT produces `401 Unauthorized`. The panel may
perform one deduplicated refresh attempt and retry the original request once.
`403 Forbidden` means the account is authenticated but lacks permission and
must not trigger a refresh loop.

## 7. Authorization Enforcement

Authentication proves the account identity. Application authorization then
checks:

* whether the account remains active;
* whether the account belongs to the resource's tenant;
* whether its tenant role permits the operation;
* whether its current assignment is active and matches the resource location;
* whether the assignment role permits the operation.

The API derives access from the authenticated account. A tenant ID, location ID,
role, or initiator ID supplied by a client is never trusted as authorization
evidence.

Staff order operations must be changed as follows:

* location listing returns only accessible locations;
* order listing and creation require access to the path location;
* order transitions derive location access through the stored order;
* accepted staff transitions record `initiator_type = USER` and the authenticated
  account ID;
* tenant administrators may use an aggregate view while managers and operators
  remain location-scoped.

Method- or service-level resource authorization must complement request-path
rules. Repository queries and transactions will later establish verified tenant
and location context for PostgreSQL Row Level Security.

## 8. HTTP Contract

The first authentication scope contains:

* `GET /api/auth/csrf` - issue or refresh the readable CSRF cookie and return its
  cookie and header names; the panel copies the cookie value into that header;
* `POST /api/auth/login` - accept `username` and `password`, then issue cookies
  and return the current authorization context;
* `POST /api/auth/refresh` - rotate the refresh credential and issue replacement
  cookies;
* `POST /api/auth/logout` - revoke the current refresh session and clear cookies;
* `POST /api/auth/logout-all` - revoke all sessions for the current account and
  clear cookies;
* `GET /api/auth/me` - return the current authorization context;
* `POST /api/locations/{locationId}/accounts` - provision a manager or operator
  with an authorized provisioner-supplied initial password;
* `PATCH /api/accounts/{accountId}/status` - activate or disable an account
  within the caller's provisioning authority; disabling revokes all sessions;
* `GET /api/orders` - return the tenant-wide aggregate queue for tenant
  administrators;
* `GET /api/tracked-orders/{trackingReference}` - retain anonymous, read-only
  access to the minimal customer-facing order projection.

Local login accepts normalized usernames only. Provisioning accepts a password
of at least 12 characters and at most 72 UTF-8 bytes, matching BCrypt's input
limit. The plaintext password is accepted only in the authenticated HTTPS
request, is never returned or logged, and is immediately replaced by its BCrypt
hash. A future single-use setup flow may replace this initial delivery policy.

The later OIDC increment adds:

* `GET /oauth2/authorization/{provider}` - begin OIDC login;
* `GET /login/oauth2/code/{provider}` - provider callback handled by Spring
  Security.

Anonymous access is limited to:

* customer order tracking;
* explicitly selected health endpoints;
* CSRF bootstrap;
* local login and refresh.

OIDC initiation and callback join this allow-list only when the later OIDC
increment is implemented.

All staff order and account-management endpoints require authentication and
resource-level authorization. The POS API will use its separate bearer API-key
mechanism.

## 9. Persistence Review

The migration provides:

* tenant-owned accounts with normalized globally unique usernames;
* optional normalized unique emails;
* password-hash storage;
* tenant roles and account status;
* at-most-one manager/operator location assignment;
* refresh sessions with token families, rotation linkage, expiry, and
  revocation.

The migration now also provides the first increment's OIDC-ready boundary:

* `external_identities` links a provider and immutable subject to an account,
  with uniqueness for both provider identity and account/provider;
* `accounts.password_hash` is nullable for a future externally authenticated
  account while remaining non-blank when present;

The following constraints remain application-level or deferred:

* the administrator-versus-location-assignment invariant is enforced only by
  application behavior unless an additional database mechanism is introduced;
* session metadata for an optional user-facing session/device list is absent.

These are OIDC-readiness changes, not an expansion of the first executable
scope. Adding the persistence concept now prevents the local-login code from
making password ownership mandatory for every future account. No OIDC
controller, callback, provider configuration, or linking workflow is implemented
in the first increment.

The database changes are made only in `V1__create_initial_schema.sql`. A local
database that already applied the previous V1 must be discarded and recreated
by the developer before the updated API is started against Docker Compose.

## 10. Security Controls

The implementation must include:

* adaptive BCrypt password hashing using Spring Security;
* generic login and account-recovery failures that prevent enumeration;
* rate limiting for login, refresh, linking, and future recovery endpoints;
* validation limits on usernames and passwords, including a generous maximum
  password length to prevent denial-of-service through hashing;
* session-family revocation on refresh-credential reuse;
* account and session security-event logging without secrets or raw tokens;
* secure external configuration for provider secrets and JWT signing keys;
* no password, access token, refresh token, or provider token in logs;
* consistent `401`, `403`, validation, and CSRF Problem Details responses;
* dependency and security-patch maintenance for Spring Security and OAuth
  components.

## 11. Verification

### 11.1 First authentication increment

Backend integration tests must cover at least:

* successful and failed local login;
* unknown username, wrong password, and disabled account with equivalent public
  failures;
* access-JWT validation and expiry;
* refresh rotation and atomic replacement;
* reuse of a consumed refresh credential and token-family revocation;
* concurrent refresh attempts;
* logout and logout-all;
* CSRF bootstrap, rotation, valid mutation, and rejected mutation;
* tenant administrator access inside and outside its tenant;
* manager access inside and outside its assigned location;
* operator order permissions and rejected account provisioning;
* disabled accounts and suspended assignments;
* authenticated audit initiator identity;
* anonymous customer tracking remaining accessible;
* unauthenticated staff endpoints returning `401` rather than redirects.

### 11.2 Later OIDC increment

When OIDC is implemented, extend the integration tests to cover:

* redirect-URI, issuer, audience, state, nonce, and callback validation;
* correct external-identity linking and account resolution;
* rejection of unlinked or conflicting external identities;
* provider email never granting tenant access by itself;
* the same Kairos principal, access JWT, refresh session, and authorization
  behavior as local login.

Run `./mvnw test` from `apps/api` and `git diff --check` before handoff. Shared
REST contract changes also require `check` in every consuming frontend.

## 12. Delivery Plan

The first increment completes:

1. the username-only login, initial-password, access-token, and refresh-session
   policy;
2. the OIDC-ready account and external-identity persistence boundary;
3. local authentication, access-JWT issuance and validation, refresh rotation,
   cookies, CSRF, logout, `/me`, failure handling, and tests;
4. staff endpoint protection, current-assignment resolution, tenant/location
   authorization, and authenticated audit identity;
5. administrator/manager provisioning and scoped account disablement with
   session revocation.
6. panel login, current-account loading, CSRF-aware staff requests, one
   cross-tab-deduplicated refresh and request retry, and logout.

The following increments remain:

1. **Implement OIDC.** Add provider configuration,
   explicit identity linking, callbacks, and the same Kairos session issuance
   used by local login. This step begins only after the local authentication and
   authorization foundation is complete.
2. **Add RLS and operational hardening.** Establish verified database security
   context, add policies, move rate-limit state to shared infrastructure before
   multi-instance deployment, and extend operational security-event retention.

## 13. Open Decisions

1. When should the provisioner-supplied initial-password flow be replaced by
   a short-lived, single-use setup link and delivery channel?
2. For the later OIDC increment, may an authenticated local user link OIDC from
   account settings, or is
   linking available only through an administrator-created setup flow?
3. When should passkeys and MFA enter the roadmap? They are excluded from the
   first increment unless scope is explicitly expanded.

## 14. Decision Log

### 2026-07-23 - Stable local JWT signing keys moved outside Spring

* The Spring API no longer has an ephemeral-key mode and always requires
  explicit public- and private-key resource locations.
* Local Compose generates a 3072-bit RSA key pair only when its dedicated named
  volume is empty, validates and reuses the pair on later starts, and mounts it
  read-only into the API container.
* Production deployments do not use the local initializer and must provide
  externally managed signing keys.
* Backend tests generate an ephemeral, explicitly test-only RSA key pair in the
  build output before the test suite starts. No test private key is stored in
  the source tree or committed to the repository.

### 2026-07-21 - Panel local-authentication integration implemented

* The panel keeps the current account in SWR, mounts staff operations only for
  an authenticated account, and returns to the login screen when session
  recovery ends in `401`.
* One shared native-`fetch` client reads the current CSRF cookie immediately
  before every unsafe request, retries a known CSRF rejection once, and never
  treats an authorization `403` as an expired session.
* Access-token recovery is serialized across tabs with the browser Web Locks
  API and a non-secret local refresh outcome marker. No access credential,
  refresh credential, password, or account data is written to browser storage.
* A protected request is retried at most once after a successful refresh.
  Login and refresh failures cannot recursively start another refresh.

### 2026-07-20 - First local-authentication contract finalized

* Local login uses normalized usernames only and authorized provisioner-supplied
  initial passwords for manager/operator accounts.
* Access JWTs use RS256 and expire after 5 minutes. Refresh credentials have a
  7-day idle lifetime and a 30-day absolute token-family lifetime.
* Concurrent use or reuse of a consumed refresh credential fails closed and
  revokes the complete token family. The panel must deduplicate refresh attempts
  across tabs in its later integration task.
* Account provisioning is location-scoped. Disabling a provisioned account
  revokes all of its refresh sessions atomically.
* The initial implementation rate-limits login attempts per client/account pair
  with broader client and account guards, and refresh attempts per credential
  with a higher per-client aggregate guard. Each API instance bounds active
  in-memory windows at 10,000 and fails closed when that capacity is exhausted.
  Distributed enforcement is part of the later operational hardening step
  before multi-instance deployment.

### 2026-07-20 - OIDC deferred beyond the first authentication increment

* The first authentication increment implements local username/password login,
  Kairos access and refresh sessions, CSRF, logout, authorization, provisioning,
  and tests.
* OAuth2/OIDC login is implemented in a later increment after the local
  authentication and authorization foundation is complete.
* The first increment must still preserve OIDC-ready database and code
  boundaries so the later login method resolves to the same account, principal,
  session issuance, and authorization model.
* Google remains the first demonstrated OIDC provider for the later increment.
* No migration was changed as part of this scope decision.

### 2026-07-20 - Kairos-owned authentication selected

* Kairos will implement and operate staff authentication with Spring Security.
* The design document now focuses only on the implementation, security controls,
  verification, and remaining Kairos authentication decisions.
* The existing access-JWT and rotating-refresh architecture remains the active
  requirement until explicitly changed.
* No migration was changed as part of this documentation decision.

### 2026-07-19 - Initial high-level pass

* Authentication design begins at the account, session, and authorization
  boundaries before narrowing into code.
* `AUTHENTICATION.md` remains synchronized with `REQUIREMENTS.md` throughout the
  implementation.
* Database changes are made only in `V1__create_initial_schema.sql`, followed by
  a developer-performed hard reset before implementation continues.

## 15. References

* [Spring Security OAuth2 and OIDC support](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
* [Spring Security CSRF protection for JavaScript applications](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
* [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
* [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
* [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
* [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
