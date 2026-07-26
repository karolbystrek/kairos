# External Integration Implementation Plan

## 1. Objective

Add a language-agnostic External Integration interface that initially supports
point-of-sale order management while remaining suitable for other third-party
systems. Tenant administrators manage integrations after signing in; public
tenant registration remains unchanged and creates no integration or secret.

The increment includes:

- External Integration management in the staff panel;
- API Key authentication, scopes, location access, rotation, and revocation;
- versioned external order endpoints;
- independently optional webhook subscriptions;
- a transactional PostgreSQL outbox and separate delivery worker;
- signed CloudEvents webhook payloads;
- durable terminal failure records.

The increment deliberately favors a straightforward proof of concept over
delivery retries, strict webhook ordering, or operational DLQ tooling.

## 2. Canonical Model

### 2.1 External Integration

An **External Integration** is a named, tenant-owned representation of one
third-party system. A POS is the first use case, not a distinct credential
model.

- A tenant may own multiple integrations.
- An integration name is required, trimmed, single-line Unicode text from 1 to
  64 characters, and unique case-insensitively within the tenant.
- Tenant administrators may create, rename, disable, re-enable, remove, and
  otherwise manage integrations only after authentication.
- Registration never creates an integration, API Key, webhook subscription, or
  secret.
- API Keys and webhook subscriptions are independently optional. An integration
  may have either, both, or neither while being configured.
- Disabling an integration immediately prevents its API Keys from
  authenticating and prevents future webhook fan-out.
- Removing an integration archives it internally. It disappears from the
  tenant's normal view, cannot authenticate, and remains available internally
  for historical attribution.
- Deliveries already persisted before an integration is disabled or archived
  continue normally using their captured configuration.

### 2.2 API Key and API Key Version

An **API Key** is the stable, named authorization grant. An **API Key Version**
is issued secret material belonging to that key.

- An integration may own zero or more simultaneously active API Keys.
- Each API Key has a required name using the same 1-to-64-character,
  single-line Unicode rules. It is unique case-insensitively within the
  integration.
- The name, scopes, location access, and optional expiration are immutable after
  issuance. A different grant requires a distinct API Key.
- Every key has at least one assigned location.
- V1 scopes are:
  - `orders:read`;
  - `orders:write`, which always includes `orders:read`.
- Write authority covers creation and lifecycle transitions for every order at
  an assigned location, regardless of which staff account, key, or integration
  created it.
- Keys have no expiration by default. An administrator may choose an expiration
  when creating the key, after which it cannot be changed.
- Revocation is immediate and irreversible and invalidates every version.
- Disabling or archiving the parent integration invalidates every key.
- Rotation creates a new API Key Version under the same named key. It preserves
  the key's name, scopes, locations, and expiration.
- The preceding version remains valid for a fixed, system-wide 24-hour grace
  period. Tenant users cannot change that period; deployment operators may
  configure the system value.
- Immediate revocation remains available.
- Kairos generates high-entropy version secrets. The full value is returned
  exactly once at initial issuance or rotation and cannot be recovered later.
- Only a non-reversible hash is stored. Management views expose only safe
  identifying metadata.
- External requests present the version secret only through
  `Authorization: Bearer <secret>`. URL and query-parameter credentials are
  rejected.
- Audit identity includes the External Integration, API Key, and exact API Key
  Version.

### 2.3 Webhook Subscription

A **Webhook Subscription** is a named destination through which an External
Integration receives selected Kairos events.

- An integration may own zero or more subscriptions.
- A subscription name is required, uses the 1-to-64-character single-line
  Unicode rules, and is unique case-insensitively within its integration.
- A new subscription starts disabled. The administrator first copies the
  one-time signing secret and configures the recipient, then enables it.
- Every active subscription selects:
  - at least one current tenant location;
  - at least one supported event type.
- Location selection is independent from API Key grants.
- V1 has no automatic “all current and future locations” option.
- Administrators may edit the subscription name, destination, event selection,
  and location selection while it is active or disabled.
- Edits affect only subsequently created deliveries. Persisted deliveries keep
  the destination and signing configuration captured at fan-out time.
- Disabling or archiving prevents future delivery creation. Already-persisted
  deliveries are still processed. Re-enabling does not replay missed events.

## 3. REST Route Redesign

### 3.1 Versioning convention

Replace the current unversioned application routes in one atomic migration.
Do not retain aliases.

- Browser-facing resource families use `/api/{resource-family}/v1`.
- External resource families use `/api/external/{resource-family}/v1`.
- Keep routes flat. Put `locationId` in validated request bodies or query
  parameters instead of nesting orders or accounts under location paths.
- Actuator remains operational infrastructure and is not moved under this
  application-resource convention.
- Update the API security mappings and every repository-owned frontend caller in
  the same migration.

Representative browser-facing routes:

```text
GET   /api/auth/v1/csrf
POST  /api/auth/v1/login
POST  /api/auth/v1/refresh
POST  /api/auth/v1/logout
POST  /api/auth/v1/logout-all
GET   /api/auth/v1/me
POST  /api/tenant-registrations/v1

GET   /api/locations/v1
POST  /api/accounts/v1
PATCH /api/accounts/v1/{accountId}/status

GET   /api/orders/v1
POST  /api/orders/v1
PUT   /api/orders/v1/{orderId}/status

GET   /api/tracked-orders/v1/{trackingReference}
GET   /api/tracked-orders/v1/{trackingReference}/events
```

Integration, key, and subscription management must follow the same flat
resource-family convention. Exact management request and response records
should be finalized with their controllers rather than introducing nested
location routes.

### 3.2 Staff order collection

Replace the current location-specific and tenant-wide staff order-list endpoints
with:

```text
GET /api/orders/v1?locationId={optional}&status={optional}
```

- Tenant administrators may omit `locationId` for the tenant-wide active queue.
- Managers and operators are constrained to their assigned location.
- The optional status filter remains limited to active queue statuses in this
  increment.
- The staff panel must not call the external adapter.

### 3.3 External order interface

The v1 order interface is:

```text
GET  /api/external/orders/v1
GET  /api/external/orders/v1/{orderId}
POST /api/external/orders/v1
PUT  /api/external/orders/v1/{orderId}/status
```

Authorization always intersects the presented key's operation scope and
assigned locations.

Collection behavior:

- requires `orders:read`;
- is cursor-paginated;
- returns only orders at locations assigned to the key;
- may include terminal orders;
- accepts optional `locationId` and one optional `status`;
- rejects an explicitly requested unassigned location.

Direct lookup outside the key's tenant or locations returns `404` without
revealing that the order exists.

Creation behavior:

- requires `orders:write`;
- carries `locationId` in the JSON body;
- shares the existing automatic/custom label semantics;
- requires the standard HTTP `Idempotency-Key` header;
- permits the integration to use its own order identifier as the opaque value;
- scopes that value to External Integration plus location;
- persists the value with the order;
- returns the existing order for an exact repeated creation;
- returns `409 Conflict` if the same value is reused with conflicting creation
  input;
- never includes the value in webhook payloads;
- remains idempotent across API Key rotation because the scope belongs to the
  integration, not a key version.

Status behavior:

- requires `orders:write`;
- uses `PUT` with the desired status in the body;
- applies the same command behavior to staff and external callers;
- returns the current order without history or event changes when the requested
  status is already current;
- applies a valid next transition exactly once;
- returns `409 Conflict` for an invalid lifecycle request.

## 4. Webhook Contract

### 4.1 Event vocabulary and payload

V1 event types are:

```text
order.created
order.ready
order.completed
order.canceled
```

A same-state idempotent status command emits no event.

Payloads use CloudEvents 1.0 structured JSON and contain:

- a stable event ID;
- event type;
- Kairos source;
- order subject;
- occurrence time;
- JSON content type;
- the complete external order snapshot, including Kairos order ID, location ID,
  label, status, `createdAt`, and `updatedAt`.

Do not include `Idempotency-Key` or other external correlation values in v1
webhooks. Every subscription delivery for the same event uses the same
CloudEvent ID and payload.

V1 provides no strict ordering or exactly-once guarantee. Workers claim
available delivery rows in best-effort FIFO order, but concurrency, HTTP
latency, and crash recovery can produce out-of-order or duplicate delivery.
Consumers use the CloudEvent ID for deduplication and order timestamps and state
to reject stale events.

### 4.2 Signing

- Every delivery is signed with HMAC-SHA256 using a subscription-specific
  signing secret.
- Sign the delivery timestamp plus exact raw request body.
- Send timestamp and versioned signatures in `Kairos-Signature`.
- Kairos generates signing secrets and reveals each version exactly once.
- The worker must recover signing material, so store it encrypted at rest rather
  than hashed.
- Supply the encryption key as an externally managed deployment secret; the
  application and images never generate it.
- Signing-secret rotation issues a new version and uses a fixed 24-hour overlap.
- New deliveries created during overlap carry signatures from both the old and
  new secret versions.
- Already-persisted deliveries retain their captured signing version.
- Administrators may retire the preceding version immediately.

### 4.3 Destination security

Production subscriptions:

- require HTTPS;
- resolve only to public addresses;
- revalidate DNS on delivery;
- reject loopback, link-local, private-network, and cloud-metadata addresses;
- never follow redirects;
- enforce bounded connection, response, and response-body handling.

An operator-controlled local profile may allow HTTP and private addresses for
development. Tenant input can never enable those relaxations.

## 5. Durable Delivery

### 5.1 Transactional outbox

- Insert one immutable outbox event in the same PostgreSQL transaction as the
  accepted order creation or transition.
- Do not roll back committed order work because later delivery fails.
- After commit, fan out one Webhook Delivery row for every active matching
  subscription.
- Persist an immutable snapshot of the destination, payload, and signing-secret
  version required by the delivery.
- Use PostgreSQL as both the durable delivery queue and dead-letter store.
- Do not use Redis Pub/Sub for webhook reliability.
- Do not introduce Kafka, RabbitMQ, or another broker in this PoC.

### 5.2 Worker deployment

- Build API and worker modes from the same `apps/api` Spring Boot codebase and
  image.
- API mode serves REST/SSE and writes outbox events.
- Worker mode exposes no business endpoints.
- Run worker mode as a separate process/service so API and worker capacity can
  scale independently.
- Claim available rows safely with PostgreSQL row locking suitable for multiple
  workers.

### 5.3 Attempt and DLQ policy

- Attempt each Webhook Delivery exactly once at the application-policy level.
- Apply one fixed, system-wide 10-second total HTTP timeout.
- Any `2xx` response succeeds.
- A network failure, timeout, redirect, or non-`2xx` response marks the delivery
  `DEAD_LETTERED`.
- Do not follow redirects.
- Do not process `Retry-After`; v1 has no automatic retry.
- Retain the immutable event, subscription, attempt outcome, response details,
  and timestamps in PostgreSQL.

V1 does not implement DLQ handling beyond storage:

- no subscription health calculation;
- no panel alert;
- no email notification;
- no acknowledgment;
- no manual redelivery;
- no cleanup workflow.

Retries, notification, inspection, acknowledgment, redelivery, and cleanup are a
future webhook-operations increment.

## 6. Persistence Shape

Add schema concepts for:

- External Integrations;
- API Keys;
- API Key Versions;
- API Key location access;
- Webhook Subscriptions;
- Webhook Subscription location access;
- Webhook signing-secret versions;
- immutable outbox events;
- recipient-specific Webhook Deliveries;
- external-creation idempotency association on orders.

Maintain enough tenant and location association for application authorization,
future RLS policies, and efficient queries without duplicating canonical order
ownership. Production migrations remain free of seeded integrations,
credentials, subscriptions, or destinations.

## 7. Implementation Sequence

1. **Route migration**
   - introduce flat versioned resource families;
   - replace existing mappings without aliases;
   - update Caddy/security matchers where needed;
   - update both frontend request modules atomically;
   - change order status commands from `PATCH` to idempotent `PUT`.
2. **Persistence and domain model**
   - add External Integration, API Key/Version, subscription/signing-version,
     outbox, delivery, location-access, and idempotency persistence;
   - preserve tenant ownership and audit identity;
   - consolidate schema changes according to the repository's current migration
     policy.
3. **Authenticated management**
   - add administrator-only application operations and panel UI for integrations,
     keys, rotation/revocation, subscriptions, signing-secret rotation, and
     enable/disable/archive actions;
   - reveal generated secrets once and require explicit confirmation that they
     were copied.
4. **API Key authentication**
   - add Bearer credential resolution without interfering with staff cookie JWTs;
   - establish verified tenant, key, scope, and location context;
   - enforce immutable grants and lifecycle state;
   - record integration, key, and version audit identity.
5. **External order adapter**
   - add create, direct read, paginated list, and idempotent status operations;
   - share domain behavior with the staff adapter without sharing its public
     representation or authentication interface.
6. **Webhook production and worker**
   - write immutable CloudEvents through the transactional outbox;
   - fan out matching subscriptions;
   - implement signed, SSRF-safe, single-attempt delivery;
   - persist success or terminal dead-letter outcome.
7. **Deployment**
   - add the worker service using the API image;
   - supply signing-secret encryption configuration;
   - keep local-only HTTP/private-destination relaxation isolated from
     production.

## 8. Verification Checklist

Static and automated verification for the implementation should cover:

- every old application route is replaced and no alias remains;
- both frontends use the new route families and pass their static checks;
- only tenant administrators can manage their tenant's integrations;
- integration, key, and subscription name normalization and uniqueness;
- API Key one-time secret handling, hashing, expiration, rotation overlap,
  revocation, and parent-integration shutdown;
- scope and location intersection for every external order operation;
- cross-tenant and cross-location non-disclosure;
- idempotent create replay and conflicting replay;
- same-state `PUT` without duplicate history or events;
- valid transition parity between staff and external callers;
- cursor pagination and location/status filters;
- subscription filtering by event type and location;
- CloudEvents payload validation and omission of idempotency values;
- raw-body HMAC verification and signing-secret overlap;
- production SSRF defenses and local-only relaxation;
- atomic order/outbox persistence;
- safe concurrent worker claiming;
- one-attempt success and dead-letter outcomes;
- best-effort delivery semantics without ordering claims;
- API failure never rolling back a committed order transition.

Run automated tests only when explicitly requested for the implementation task,
in accordance with the repository workflow.

## 9. Explicitly Deferred

- OpenAPI document and Swagger-style rendered documentation; these are next in
  line after the initial integration increment.
- Generated SDKs and formal automated public-contract checks.
- Automatic webhook retries and `Retry-After`.
- Strict per-order or per-subscription delivery ordering.
- Exactly-once webhook guarantees.
- DLQ views, alerts, email notification, health status, acknowledgment,
  redelivery, and cleanup.
- A dedicated message broker.
- Automatic all-current-and-future-location webhook subscriptions.
- Idempotency values in webhook payloads.
- Application-level request throttling; production ingress remains responsible
  under the existing architecture.
