# HTTP resource families and contracts

### Current HTTP resource families

The implemented browser-facing contract consists of:

```text
GET    /api/auth/v1/csrf
POST   /api/auth/v1/login
POST   /api/auth/v1/logout
POST   /api/auth/v1/password
POST   /api/auth/v1/password-reset
POST   /api/auth/v1/password-reset/confirmation
GET    /api/auth/v1/me
POST   /api/tenant-registrations/v1

GET    /api/locations/v1
GET    /api/accounts/v1
PUT    /api/accounts/v1/{accountId}/status

GET    /api/account-invitations/v1
POST   /api/account-invitations/v1
DELETE /api/account-invitations/v1/{invitationId}
POST   /api/account-invitations/v1/{invitationId}/resend
POST   /api/account-invitation-previews/v1
POST   /api/account-invitation-redemptions/v1

GET    /api/orders/v1
GET    /api/orders/v1/events
POST   /api/orders/v1
PUT    /api/orders/v1/{orderId}/status

GET    /api/tracked-orders/v1/{trackingReference}
GET    /api/tracked-orders/v1/{trackingReference}/events
POST   /api/tracked-orders/v1/{trackingReference}/review-actions
POST   /api/usage-analytics/v1/actions
GET    /api/analytics/v1/report?from&to&zone[&locationId]
GET    /api/platform-analytics/v1/report?from&to&zone
GET    /api/platform-analytics/v1/health

GET    /api/customer-notifications/v1/configuration
PUT    /api/customer-notifications/v1/subscription
POST   /api/customer-notifications/v1/subscription-replacement
DELETE /api/customer-notifications/v1/subscription
DELETE /api/customer-notifications/v1/enrollments
```

The accepted Locations and unified administrative-lifecycle increment changes
and extends that browser contract as follows:

```text
GET    /api/locations/v1
POST   /api/locations/v1
PUT    /api/locations/v1/{locationId}
PUT    /api/locations/v1/{locationId}/reviews
PUT    /api/locations/v1/{locationId}/status
DELETE /api/locations/v1/{locationId}

GET    /api/accounts/v1
PUT    /api/accounts/v1/{accountId}/status
DELETE /api/accounts/v1/{accountId}
```

#### Authentication and registration responses

Authentication returns one current-Account shape with Account UUID, email,
tenant UUID, tenant role, optional location assignment, and capabilities. No
username, Account-kind discriminator, password, or provider credential is
returned. Verified login/invitation registration and email confirmation return
`200` with the current Account and an HttpOnly session cookie. Public
registration and unverified login without an Account instead return verification
state (`verificationRequired`, `email`, `resendAvailableAt`) with a pending
session that grants no tenant authority. GET/POST `/api/auth/v1/verification`
reads state/confirms `{code}`; POST `/api/auth/v1/verification/resend` requests a
new code after the cooldown. Registration accepts
email/password/passwordConfirmation; only member registration requires `token`.
Public registration creates no location. Registration input failures use Problem
Details with a `fieldErrors` object mapping `email`, `password`, or
`passwordConfirmation` to safe corrective messages; identity conflicts return
`409` with an Email field error. First-location onboarding uses the ordinary
`POST /api/locations/v1` contract and its name validation, enabled initial state,
and required IANA `timeZone`, taken from the browser and immutable afterwards.
Anonymous login, local logout and both registration families remain CSRF
protected. Current Account and password changes require eligible
authentication. Successful password changes and logout return
`204`; generic credentials failure is `401`, input errors `400`, eligibility
or signed-in registration rejection `403`, and identity conflicts `409`.
Tenant Registration Invitation resource families no longer exist.

#### Location and account mutations

Location creation accepts the display name and optional `googleReviewUrl` and returns the new enabled
representation with `201`. Rename accepts only the display name. The location
status operation accepts only `ENABLED` or `DISABLED`; archival remains the
Delete operation. Rename and status return the updated representation with
`200`, while Delete returns `204`. Repeating the current normalized name or
status and repeating a successful Delete are idempotent. Location representations
include nullable `googleReviewUrl`. The administrator-only reviews operation
accepts `{ "googleReviewUrl": "https://g.page/.../review" }`, or null/blank to
disable invitations, and returns the updated location with `200`. Invalid links
return `400`; changing/removing the link invalidates outstanding invitations.

Every location mutation requires tenant-administrator authority and locks the
tenant-scoped location before checking or changing it. Cross-tenant targets are
indistinguishable from an unknown target. Rename and status operations also
treat an archived target as unknown, while repeating Delete for an archived
location in the caller's tenant remains idempotently successful. Name
conflicts, attempting to delete an enabled location, and attempting to disable
a location with an active order produce safe `409` responses. Attempting to
delete the last non-archived location also returns `409`, with stable problem
type `urn:kairos:problem:location-last-location`. The active-order
conflict has a stable problem type so the panel can offer its direct **View
orders** recovery. Each successful disable, enable, or Delete transition and
all of its account, authentication-cutoff, invitation, credential-grant, and subscription
side effects commit atomically; a rejected transition changes nothing.

Account status accepts only `ENABLED` or `DISABLED` and returns the updated
representation. Delete accepts an enabled or disabled manageable member
account, archives it atomically with its authentication and invitation side
effects, and returns `204`. Account lists omit archived accounts, a repeated
Delete for an archived account in the caller's management scope is idempotent,
and status operations treat archived, cross-tenant, and otherwise unmanageable
targets as unknown.

#### Invitation contracts

The Account Invitation list, creation, resend and revocation operations are authenticated and
scoped by the caller's account-management authority. Preview and redemption are
anonymous, accept the bearer token only in their validated request bodies,
retain normal browser CSRF protection, and never log those bodies. Preview
returns the fixed recipient email, restaurant administrator email, location
name, role, and expiration needed by
the registration page. Creation requires email, location, and role; list and
creation responses include the recipient email and delivery state, never a token
or link. `GET` returns a JSON array of the complete authorized history, ordered by
creation time descending, then ID descending for stable ties. The list has no
paging or sorting parameters or response metadata. Lifecycle states are
`PENDING`, `REDEEMED`, `REVOKED`, `REPLACED`, and derived `EXPIRED`; delivery
states are `QUEUED`, `SENT`, `FAILED`. Creation defaults `replaceExisting` to
`false` and returns `409` for an active invitation to that tenant/email. Explicit
`true` replaces it only with authority over both old and new targets.
`POST /{invitationId}/resend` requires `{replaceExisting:true}` and an active
pending invitation; it returns a fresh replacement with a new seven-day token. Redemption rejects an email
that differs after normalization with an Email field error and `400`, before
provider provisioning and again inside the local redemption transaction. Redemption removes the token fragment from browser
history after success or a terminal response. Validation failures return `400`,
an available invitation with conflicting email or provider identity returns `409`, a
known expired, revoked, replaced or redeemed invitation returns `410`, an unknown token
returns `404`, and any valid existing panel session receives `403`.

#### Integration and order contracts

The authenticated administrator management families are
`/api/external-integrations/v1`, `/api/api-keys/v1`,
`/api/webhook-subscriptions/v1`, and `/api/webhook-signing-secrets/v1`. Their
lifecycle operations use the flat resource-family convention and never accept
client-supplied tenant ownership. API Keys support only list, create, and
`DELETE /api/api-keys/v1/{apiKeyId}`.

The implemented External Integration order contract is:

```text
GET    /api/external/orders/v1
GET    /api/external/orders/v1/{orderId}
POST   /api/external/orders/v1
PUT    /api/external/orders/v1/{orderId}/status
```

A location's menu uses `GET`, `PUT` and `DELETE
/api/locations/v1/{locationId}/menu`; `PUT` carries the complete `items` list
and creates or replaces the menu, `DELETE` returns `204` whether or not a menu
existed, and responses carry a nullable `menu` with `items` and `updatedAt`.
See [menus](menus.md).

Staff order listing accepts an optional `locationId` and active `status`.
External listing accepts an opaque cursor plus optional authorized
`locationId` and `status`. Order creation carries `locationId`, an optional
`label` and optional `items`, each with a menu-item `name` and `quantity`.
External creation additionally requires `Idempotency-Key`. Staff, external and
webhook order representations include the integer `number`, nullable `label`
and `items`; customer representations include `number` and nullable `label`
but not items. External integrations read a location's menu through
`GET /api/external/locations/v1/{locationId}/menu` with the staff menu
response shape. Desired-state updates use idempotent `PUT`; a same-state
request returns the unchanged representation without another customer event or
outbox event.

Tracked-order representations include nullable `reviewInvitation` with `dueAt`,
`locationName`, and `googleReviewUrl` only while completion remains eligible.
Possession of the ordinary tracking reference grants this read-only follow-up;
no customer identity or staff access is introduced. An eligible due invitation
also permits the bounded review action write described in
[usage analytics](usage-analytics.md); it does not permit order mutations.

#### Container runtime and environment

Docker Compose builds immutable production-mode application images locally and
does not synchronize source files or run Fast Refresh or Spring Boot DevTools.
Both Next.js applications run their standalone build output, and the customer
build generates the Serwist service worker before the runtime image is
assembled. The packaged Spring Boot API runs Flyway migrations and scheduled
webhook and customer-push background jobs in the same application process.
Applying source changes requires rebuilding and recreating the affected
application container. NGINX uses shared upstream zones with runtime resolution
through Docker's embedded DNS and a five-second refresh interval for all three
application services. It must follow container IP changes without restart, so
recreating services cannot leave hostnames routed to a stale or reassigned IP.

The customer Next.js application serves the generated service worker with a
root scope, JavaScript content type, restrictive content-security policy, and
explicit no-cache headers so update checks do not reuse a stale script.

The Spring API uses one configuration. The root environment file supplies
origins, credentials, identities, and delivery policies, while private keys use
stable `/run/secrets` paths. Secure, host-only `SameSite=Lax` cookie behavior is
an application invariant. `.env.example` records the environment-variable surface.
