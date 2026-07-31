# Kairos Architecture and Requirements

## 1. Purpose and Scope

Kairos is a multi-tenant virtual pager system for restaurants. A tenant represents a customer organization, such as an independent restaurant or restaurant chain, and owns one or more physical locations. A customer scans a QR code assigned to an order and opens a lightweight web application that displays the current order state and receives real-time updates. Restaurant staff manage orders through a separate administrative panel. External systems, initially point-of-sale systems, can create and update orders through a versioned REST API and receive webhooks.

Kairos replaces failure-prone physical restaurant pagers without requiring a
customer to install a chain-specific native application for a short-lived
transaction. It remains independently usable by restaurant staff while
offering an optional, language-agnostic integration boundary for point-of-sale
systems.

The core system consists of three independently deployable applications:

* **Customer application:** anonymous, mobile-first Next.js PWA for order tracking.
* **Staff panel:** authenticated Next.js application for queue management and QR-code generation.
* **API:** Spring Boot application responsible for all business rules, security, persistence, real-time communication, and external integrations.

Native mobile applications, App Clips, and Instant Apps may be added later, but
the browser experience must work without installing the customer PWA. Web Push
is an optional enhancement that requires explicit customer consent; foreground
REST and SSE tracking remains the primary experience.

## 2. Technology and Ownership Decisions

### 2.1 Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, and HeroUI 3. They remain separate because they serve different audiences and have different authentication, PWA, caching, and release concerns. The customer application uses a custom TypeScript service worker built with Serwist in configurator mode and owns its offline snapshots in IndexedDB.

HeroUI is the component library for both applications. Zod validates frontend-owned form input and server-sent event payloads. Native `fetch` is the HTTP transport inside small handwritten REST request modules. SWR manages REST-backed client state in both frontends where caching, request deduplication, mutations, focus revalidation, or reconnect revalidation applies. React effects are reserved for synchronization with external systems rather than routine REST request orchestration.

The Spring API retains domain authority. Frontend controls and redirects provide user experience, while the backend enforces authorization.

### 2.2 Backend

The backend uses Java 25 and Spring Boot 4. It is the only system of record and owns:

* order lifecycle and transition rules;
* staff authentication and authorization;
* tenant, location, and staff-access resolution;
* PostgreSQL persistence and migrations;
* Server-Sent Events publication;
* Redis-backed coordination and pub/sub;
* External Integration authentication, REST access, and webhooks;
* transactional order-event outbox processing;
* anonymous customer notification enrollment and durable Web Push delivery.

Browser-facing API requests go directly to the Spring API. The local HTTP
environment uses credentialed CORS scoped by frontend origin and browser
resource family. Next.js route handlers are limited to frontend rendering
concerns.

### 2.3 API contracts

REST is the integration boundary for frontends and third-party systems. During the first walking vertical slice, the endpoints are implemented as ordinary Spring MVC controllers and each frontend uses small handwritten TypeScript response types with native `fetch`, wrapped by SWR in Client Components that need server-state synchronization. The slice does not expose an OpenAPI document or generate REST clients. Next.js Server Actions and proxy route handlers do not replace or wrap the Spring REST boundary.

External Integration REST and webhook contracts remain language-agnostic and
use versioned resource families. OpenAPI publication, rendered reference
documentation, generated SDKs, and automated public-contract checks remain the
next integration increment. Introducing them must not move business rules or
API ownership out of the Spring application.

## 3. Functional Requirements

### 3.1 Order lifecycle

An order follows a controlled lifecycle:

1. **In preparation:** staff or an authorized External Integration creates an accepted order for an accessible location. The API immediately places it in preparation and returns its customer-visible label and QR-code tracking reference.
2. **Ready for pickup:** staff or an External Integration marks the order ready and connected customer pages refresh their visible state.
3. **Completed:** the order is collected, the live session ends, and later scans show a terminal state.
4. **Canceled:** the order is canceled before completion, connected customer pages refresh, and the live session ends.

There is no separate `CREATED` state: every created order is automatically accepted into `IN_PREPARATION`. From there it may become `READY` or `CANCELED`; a ready order may become `COMPLETED` or `CANCELED`; completed and canceled orders are terminal. The customer cannot request transitions.

The backend validates every transition regardless of whether it originates from the panel or an External Integration. Every accepted resulting state is recorded in an append-only history. The prior state is derived from the preceding history entry, with the first `IN_PREPARATION` entry representing creation.

### 3.2 Customer application

The customer application must:

* open directly from an order-specific QR code without login or installation;
* display the current order state before establishing real-time communication;
* open an anonymous Server-Sent Events stream scoped only to the referenced order;
* validate every received event before using it as a signal to revalidate the authoritative REST state;
* rely on the browser's native `EventSource` reconnection and fetch the current state through REST whenever the stream opens or reopens;
* poll REST approximately every 15 seconds while the event stream is disconnected, and stop fallback polling when it reconnects;
* reconcile through REST on focus and browser connectivity restoration;
* present an active order primarily by its label and status, show a status
  timestamp only when the displayed data is explicitly stale, and rely on
  automatic reconciliation rather than an always-visible manual refresh;
* provide clear terminal views for completed, canceled, or unknown orders;
* keep completed and canceled tracking references readable without an expiration
  policy in the current scope;
* use a service worker for an installable shell, explicit offline fallback, Web
  Push handling, and application badges without treating cached order REST
  responses as authoritative;
* support current Chrome on Android, Safari on iOS and iPadOS, and current
  desktop Chrome and Firefox progressively, while preserving the ordinary web
  experience when an optional PWA API is unavailable.

The customer application is installable as one globally branded **Kairos** web
application while remaining fully usable without installation. Installation is
browser- or operating-system-initiated; Kairos does not show its own install
prompt in the current scope. The installed application uses standalone display
mode, a light-only appearance, and no orientation lock. Android Chrome and iOS
Safari are the required installation targets, while desktop browsers must
continue to provide the ordinary web experience.

The manifest uses `Kairos Order Tracking` as its full name and `Kairos` as its
short name. Its stable application ID and scope cover the complete customer
origin. Home uses the ordinary root start URL. An order page exposes an
order-aware manifest with the same stable identity and a start URL that enters
through Home with only the tracking reference required for bootstrap.

An installation initiated from an order page opens that order on the first
installed launch while retaining one stable Kairos application identity and
origin-wide scope. Each browser or installed-app context subsequently retains
its own local recently tracked orders. The contexts are not synchronized with
one another, with another device, or through the backend. This local collection
does not establish customer identity or order ownership.

The local collection uses IndexedDB and retains only distinct active
`IN_PREPARATION` or `READY` orders. Reopening an order refreshes its stored
label, status, and server-provided `updatedAt` snapshot and moves it to the front
of the collection. A terminal REST response or accepted push transition removes
the order from that active collection. A short-lived terminal tombstone prevents
an older response or push from resurrecting it. Home renders these stored
summaries without opening SSE streams; when notifications are enabled it may
reconcile the current browser push subscription and active enrollments with the
API. Selecting an entry opens its tracking page, where the normal authoritative
REST and SSE flow resumes.

After the one-time installation launch, opening the installed application
directly reopens the most recently tracked order when its stored status is
`IN_PREPARATION` or `READY`. When no readable active collection exists, it opens
Home. Home displays a centered empty state when IndexedDB is empty, corrupt,
unavailable, or inaccessible, and offers one confirmed action to clear all
locally tracked orders. Terminal tracking views provide a home icon; active
tracking views do not.

Order REST endpoints are network-only in the service worker. The application
shell and a dedicated offline route are precached, while application code
explicitly reads last-known order snapshots from IndexedDB after a network
failure. An offline view must label its data and timestamp as last known and
must not imply that it is current. When no snapshot exists, the application
shows an offline explanation rather than manufacturing an order state.

Notification consent belongs to the complete Kairos PWA context rather than an
individual order. Kairos requests browser permission only from a direct user
action. On iOS and iPadOS outside standalone Home Screen mode, the control first
explains how to add Kairos to the Home Screen instead of calling the permission
API. A denied permission shows browser-settings guidance. Once enabled, every
locally active order is automatically reconciled as an enrollment for the
current browser push subscription. New active orders are enrolled silently, and
terminal orders are removed after their final notification delivery has been
materialized. The API limits a subscription to ten contexts per order.

The application exposes one notification control on Home and active order
views. Disabling notifications durably retires the current subscription and all
of its enrollments before removing the browser subscription. Clearing tracked
orders removes the selected active-order enrollments but preserves the app-level
notification preference for future orders; both actions require a network
connection so the UI does not make a false backend-cleanup promise.
Browser-initiated subscription replacement is reconciled once from the service
worker and idempotently retried on the next application start if needed.

Background notifications are generated for `READY`, `COMPLETED`, and `CANCELED`
transitions only. Their title is the global Kairos brand, their body describes
the state without disclosing the order label, and their click target is the
order route. Notifications use one replacement tag per order. The service worker
validates a versioned payload, deduplicates its stable event ID, and applies the
transition only when it is reachable from the locally stored state graph.
Malformed or unprocessable payloads produce a generic, privacy-preserving
notification. The foreground tracking page continues to use REST and SSE and
may issue one short vibration pulse for a newly observed transition when the
browser permits it; no custom notification vibration pattern or sound is used.

The application badge, where supported, is the number of locally active orders
enrolled for notifications. Kairos does not add Background Sync, Periodic
Background Sync, Screen Wake Lock, synthetic audio, or a separate background
polling promise. The service worker does not force `skipWaiting` or immediately
claim existing clients; an update activates through the browser lifecycle.

### 3.3 Staff panel

The staff panel must:

* allow an anonymous visitor to register a tenant, its first location, and its first administrator through the dedicated onboarding flow;
* require an authenticated internal account;
* show only locations and orders accessible to the account;
* allow tenant administrators to switch between locations or view an aggregate queue;
* create labeled orders for an accessible location and display their customer QR codes;
* offer automatic labels by default and allow a custom label before creation;
* display and refresh only active order queues by location;
* allow only valid order transitions permitted by the account's role;
* allow tenant administrators to provision location managers and operators within their tenant;
* allow a location manager to provision operators only for the manager's assigned location;
* allow tenant administrators to manage External Integrations, API Keys, and webhook subscriptions;
* provide clear feedback for stale data, rejected transitions, expired sessions, and network failures;
* keep messages and administrative summaries user-oriented by omitting routine
  background-refresh narration, arbitrary backend error details, internal
  integration or credential identifiers, and raw permission tokens.

### 3.4 External Integration API and webhooks

An External Integration is a named, tenant-owned representation of a
third-party system. A point-of-sale system is the first use case rather than a
distinct credential model. Tenant administrators can create, rename, disable,
re-enable, and archive integrations after signing in. Tenant registration
creates no integration, API Key, subscription, or secret.

An integration may independently own API Keys and webhook subscriptions. Each
stable, named API Key has immutable scopes, access to one or more current tenant
locations, and an optional immutable expiration. `orders:write` includes
`orders:read`. Keys can be revoked immediately and irreversibly. Rotation
creates a new secret version while the immediately preceding version remains
valid for a deployment-configured 24-hour grace period. The full high-entropy
secret is revealed once and only a non-reversible hash is stored.

The versioned external order API supports cursor-paginated listing, direct
lookup, idempotent creation, and idempotent desired-state updates. Authorization
always intersects the presented key's scopes and locations. Direct lookup
outside that intersection returns `404`. Creation requires an
`Idempotency-Key`, scoped to integration plus location, and returns the original
order for an exact replay or `409` when the same value is reused with different
creation input. The value is opaque, case-sensitive, and limited to 255 UTF-8
bytes. Lists use an opaque cursor over descending creation time and order
identity, default to 50 results, and accept at most 100. A same-state status
request changes no history and emits no event.

Webhook subscriptions independently select at least one location and supported
order event type. A new subscription is disabled until its one-time signing
secret has been copied and the recipient configured. Each delivery uses a
CloudEvents 1.0 structured JSON body containing a complete external order
snapshot, `urn:kairos:orders` as its source, and `orders/{orderId}` as its
subject. It is signed over its timestamp and exact raw body with HMAC-SHA256.
The external REST representation includes the customer tracking reference;
webhook snapshots omit that reference together with idempotency and other
external-correlation values.
Signing-secret rotation has a fixed 24-hour overlap, during which new deliveries
carry signatures from both the current and immediately preceding versions. The
`Kairos-Signature` header uses
`t=<epoch-seconds>,v1=<lowercase-hex>[,v1=<lowercase-hex>]`, and each signature
covers the UTF-8 bytes of `<epoch-seconds>.<exact-body>`.

Order changes create one immutable, channel-neutral outbox event in the same
database transaction as the order and history mutation. Independent background
pipelines inside the Spring API fan out recipient-specific webhook and
customer-push delivery rows.
The webhook pipeline attempts each delivery once at the application-policy level.
Any redirect, timeout, network failure, or non-`2xx` response is durably
dead-lettered; v1 performs no automatic delivery retry. API process crash recovery
may result in duplicate delivery, so consumers deduplicate by the stable
CloudEvent ID and reject stale order snapshots by state and timestamp.

Production webhook destinations require HTTPS and public network addresses.
DNS is revalidated for delivery, redirects are not followed, and connection,
response, and response-body handling are bounded by a fixed ten-second total
HTTP timeout. Only an operator-controlled local profile may relax HTTP and
private-address restrictions.

## 4. Authentication and Security

### 4.1 Staff authentication

Spring Security is the sole authentication authority.

The target authentication model supports:

* Kairos-managed accounts with normalized usernames and BCrypt-hashed passwords;
* OAuth2/OIDC login, initially demonstrated with Google but configurable for another provider;
* linking an external provider identity to a Kairos account and its owning tenant;
* the same application authorization model regardless of login method.

After either login method, Spring issues a short-lived signed access JWT and a
rotating refresh credential. Browser credentials are transported in `Secure`,
`HttpOnly`, `SameSite=Lax`, host-only cookies issued by the API. The local HTTP
environment relies on browsers' localhost secure-context exception while
retaining those cookie attributes. Cookie-authenticated state-changing requests
require CSRF protection.

The implemented local-authentication policy uses an RS256 access JWT with a
five-minute lifetime, a seven-day refresh idle lifetime, and a 30-day absolute
refresh-family lifetime. The access cookie is `__Host-access-token`, the
refresh cookie is `__Host-refresh-token`, and both use `Path=/` with no
`Domain` attribute. The host-only CSRF cookie is `__Host-XSRF-TOKEN`. The
credentialed CSRF bootstrap response returns its current token value so a
frontend on another trusted subdomain can cache the token only in its active
execution context and send it as `X-XSRF-TOKEN` on unsafe requests. The
customer service worker independently
bootstraps a current token for unsafe background work and never treats a
persisted token as authoritative. A recognized missing or invalid CSRF response
clears the execution-context token, bootstraps again, and replays the unsafe
request at most once. Login, logout, logout-all, and any future operation that
rotates CSRF state are followed by a fresh bootstrap. No password, access
credential, refresh credential, provider token, current-account record, or
CSRF token may be stored in browser-managed persistent storage.

The access JWT contains the issuer, audience, account ID subject, tenant ID,
tenant-level role, issue and expiry times, and unique token ID. It does not
contain the mutable username, email, or location assignment. The API resolves
the current assignment and current account eligibility from PostgreSQL.

Refresh credentials are opaque random values stored only as cryptographic
hashes. Rotation locks the matching session, atomically consumes it, creates a
replacement in the same family, and rechecks current account eligibility.
Reuse of a consumed credential revokes the complete family. Cooperating panel
requests serialize authentication-cookie mutations with a browser Web Lock
when available, recheck the current account after acquiring it, and perform at
most one refresh plus one replay of the original request. An authorization
`403` never starts refresh. Login and anonymous tenant registration do not
start automatic recovery; registration also does not acquire the
authentication-cookie lock.

Public tenant onboarding is the only anonymous account-creation flow. It atomically creates one tenant, its first location, and its first active administrator. The administrator requires a normalized, globally unique email address in addition to its normalized username and BCrypt-hashed password. Registration does not issue authentication cookies or sign the administrator in. During rapid development, tenants, locations, accounts, and orders have no separate display-name fields; accounts are presented by username and the other concepts by their full stable identifiers.

Standalone accounts are provisioned rather than self-registered. Each account belongs directly to one tenant. A tenant administrator has tenant-wide access; a location manager or operator has at most one location assignment. Location operator accounts are device-oriented, and a location uses a separate account for each panel device that needs independent credentials or revocation. Tenant administrators may provision managers and operators, while location managers may provision only operators for their own location.

The tenant-registration endpoint is anonymous but retains normal CSRF protection because it is invoked from the browser panel origin. The current local slice does not implement application-level rate limiting for tenant registration, login, or refresh. Before public deployment, a dedicated API gateway in front of Spring must enforce request throttling for these routes, preserve a trustworthy client-address boundary, and prevent direct access that bypasses the gateway. Spring remains the authentication authority and does not duplicate the gateway's request-rate counters.

One browser profile on the staff-panel origin represents one signed-in account because its tabs share the same authentication cookies. Same-account tabs may be used on a best-effort basis, but the panel does not promise immediate cross-tab interface synchronization. Different accounts in different tabs of one browser profile are unsupported; simultaneous accounts require separate browser profiles, private browsing contexts, or devices. The panel provides one primary operational workspace, including administrator location switching and aggregate views, rather than depending on browser tabs for core workflows.

The authenticated principal carries the account identity, owning tenant, and tenant-level role needed for authorization. The backend resolves the current location assignment rather than embedding mutable assignment data in the JWT. The API derives tenant and location access from the authenticated account and never trusts a tenant or location identifier merely because it was supplied by the client.

The API provides operations for CSRF bootstrap, local login, session refresh,
logout, logout-all, and retrieving the current account identity.
OAuth2/OIDC initiation, callback handling, and explicit external-identity
linking remain deferred. A future provider email alone must never create an
account, select a tenant, or grant access; login resolves an already linked
immutable provider subject to the same Kairos account and session issuance used
by local login.

### 4.2 Customer access

Customer order tracking is anonymous. QR codes use high-entropy, unguessable order references and expose only the minimum customer-facing order information. Possession of a tracking reference grants read-only access to that order and never authorizes staff operations or access to tenant data.

Customer notification enrollment is also anonymous and treats the complete
browser push subscription capability as its credential: endpoint, P-256 client
public key, and authentication secret must all match for mutation or removal.
Unsafe notification requests retain normal CSRF protection. Enrollment requires
each high-entropy tracking reference and does not create a durable customer
identity or reveal whether an unrelated order exists.

Push endpoints must use HTTPS and resolve to public network addresses in
production. DNS is revalidated for delivery, pinned for the connection,
redirects are not followed, and fixed timeouts and response limits apply. Only
an operator-controlled local profile may relax the public-address restriction.
The endpoint and authentication secret are encrypted at rest with an externally
managed key; the endpoint is additionally stored as a non-reversible hash for
lookup. Logs, error responses, and metrics must not expose complete subscription
endpoints or key material. VAPID signing keys are externally managed and never
generated by the application or image.

### 4.3 External Integration access

External clients authenticate only with an API Key version secret in the
standard `Authorization: Bearer` header. URL and query-parameter credentials are
not accepted. Authentication establishes the owning tenant, External
Integration, stable API Key, exact API Key Version, scopes, and location grants.
Disabling or archiving an integration immediately prevents all of its keys from
authenticating and prevents future webhook fan-out. Persisted deliveries retain
their captured destination and signing configuration and continue normally.
Integration and subscription fan-out eligibility is bounded by the start of
their current enabled interval, so delayed outbox processing cannot replay
events missed while either one was disabled.

### 4.4 Tenant isolation

All tenant-owned data is protected at both application and database levels. PostgreSQL Row Level Security provides defense in depth. Every transaction accessing tenant-owned rows must establish tenant and location access from a verified account or External Integration credential.

Orders derive tenant ownership through their physical location rather than storing a second direct tenant relationship. RLS policies for orders and their dependent records must verify the location's tenant and, for staff or External Integration operations, the principal's access to that location. A location cannot be reassigned to another tenant after operational data has been created; such a change requires an explicit migration.

Each location stores an IANA time-zone identifier initialized to `UTC`. The current order-numbering increment deliberately uses UTC rather than the stored location setting. The API runtime, injected application clock, and database session use UTC without an environment-selectable alternative in this scope. Location-specific civil-time numbering is deferred.

## 5. Persistence Requirements

The database schema must include tables covering the following concepts. Names and nonessential columns are implementation decisions.

* **Tenants:** stable identity and integration configuration.
* **Locations:** physical restaurant belonging to one tenant, with operational information and a time-zone identifier initialized to `UTC`.
* **Accounts:** stable identity, direct ownership by one tenant, normalized local login identifier and credential hash when applicable, optional normalized globally unique email except where the onboarding contract requires it, tenant-level role, and account state.
* **External identities:** provider and immutable provider subject linked to an account.
* **Location assignments:** relationship between a non-admin account and its accessible location, including a manager or operator role and assignment state. The current account model permits at most one assignment per account; the relationship remains normalized so that this cardinality can be changed explicitly in a future migration.
* **Orders:** public tracking identity, owning location, immutable customer-visible text label, current state, and lifecycle timestamps. Tenant ownership is derived through the location.
* **Order history:** order association, resulting state, acceptance time, and the initiator category and identity when known; records are append-only and have an unambiguous order. Initiator categories distinguish users, external integrations, and system actions without coupling history to one authentication mechanism.
* **Automatic order-label allocation:** when a custom label is omitted, the concurrency-safe decimal label is one greater than the count of all orders created for the location during the current UTC date. Custom-labeled orders advance this daily ordinal. No separate numbering date, numeric value, label source, or allocation state is stored on the order; the allocated text is persisted only in its label.
* **Refresh sessions:** account/session association, hashed rotating credential, expiry, and revocation state.
* **External Integrations:** tenant association, normalized name, and enabled, disabled, or archived lifecycle state.
* **API Keys and versions:** immutable scopes, expiration and location grants on the stable named key; hashed one-time secret material and overlap validity on each version.
* **Webhook subscriptions:** integration association, normalized name, destination, enabled/disabled/archive state, selected locations, and selected event types.
* **Webhook signing-secret versions:** encrypted recoverable signing material, rotation overlap, and retirement state. Encryption keys are externally managed and never generated by the application or image.
* **Order outbox events:** immutable event identity, order/location and tracking association, exact serialized webhook payload, resulting order state, occurrence time, and independent webhook and customer-push fan-out state.
* **Webhook deliveries:** recipient-specific captured destination, payload, signing versions, claim state, one attempt outcome, bounded response details, and durable success or dead-letter state.
* **Customer push subscriptions:** a browser-context capability with hashed
  endpoint, encrypted recoverable endpoint and authentication secret, client
  P-256 public key, VAPID-key fingerprint, expiration when supplied by the
  browser, and last-observed time.
* **Customer push enrollments:** the many-to-many association between a complete
  browser push subscription and active order tracking references.
* **Customer push deliveries:** one mutable retry row per outbox event and
  subscription, including the privacy-minimal payload, freshness deadline,
  claim state, attempt count, next-attempt time, bounded outcome details, and
  accepted, superseded, canceled, expired, or terminal dead-letter state.
* **External order creation identity:** integration-and-location-scoped idempotency value and canonical creation-input fingerprint associated with the created order.

Tenant ownership may be direct or derived through an unambiguous relationship such as order to location to tenant. Tables must carry enough association for RLS enforcement and efficient access checks without duplicating ownership data by default. Secrets, passwords, refresh credentials, and API keys must never be stored in plaintext.

Order labels are trimmed, single-line text with a maximum of 32 characters. Automatic labeling is requested by omitting the custom label and uses the order's one-based creation ordinal among all orders at its location during the current UTC date. A provided blank label is invalid. Labels preserve casing, may contain ordinary Unicode text, and are not unique; staff remain responsible for avoiding ambiguous duplicates. Labels cannot be edited after order creation.

## 6. Real-Time Communication

The API exposes a Server-Sent Events stream for each active customer tracking
reference. In the local environment the customer application connects to that
stream directly across the explicitly allowed HTTP origins. The stream is
anonymous and read-only: possession of the high-entropy tracking reference
grants access to that order's events but never authorizes a command. The
customer validates each compact event with Zod and treats it only as an
invalidation signal; SWR then retrieves the authoritative customer
representation through REST.

Order-state events are published only after the PostgreSQL transition and history transaction commits. Redis Pub/Sub distributes each event to every live API instance, and each instance forwards it to its locally connected SSE clients for that tracking reference. Publishing and subscription are mandatory application behavior and have no feature flag. Redis remains part of application health reporting and its health contributor must not be disabled. The publishing instance must not turn a committed transition into a failed command response when Redis is unavailable.

Redis Pub/Sub and SSE are intentionally non-durable. The client reconciles through REST when the stream opens or reopens, on focus, and after browser connectivity returns. Cache invalidation may clear the internal SWR entry before refetching, but the customer UI retains the last authoritative order during that request so the active stream is not torn down and reopened. While SSE is disconnected it falls back to approximately 15-second REST polling. No periodic safety request runs while SSE appears healthy, so the current increment accepts the rare possibility that an after-commit Redis publication failure leaves a page stale until another reconciliation trigger.

A terminal transition produces the final invalidation and ends the live stream. Opening an already terminal order returns its REST state without maintaining an SSE connection. Servlet async and error redispatches continue processing the authorization decision made for the original request instead of being treated as new protected commands.

Web Push complements rather than replaces this foreground contract. `READY`,
`COMPLETED`, and `CANCELED` order events are durably fanned out to enrolled
browser subscriptions. The service worker treats a push as a last-known
transition snapshot and notification trigger; opening or focusing the
application still reconciles authoritative state through REST. At-least-once
delivery, unordered push services, and multiple subscriptions require stable
event IDs, state-graph monotonicity, replacement tags, and pre-submission checks
against current PostgreSQL order state. Authenticated staff queue streaming
remains deferred. Future genuinely bidirectional features may introduce
WebSocket independently rather than changing the SSE or Web Push contracts.

## 7. Routing and Deployment

The independently deployable services are exposed directly in the local Docker
Compose environment.

* The customer, staff-panel, and API origins are configured in the root
  environment file from the values documented in `.env.example`.
* Browser-facing REST and SSE requests go directly to the API origin. Spring
  allows credentialed CORS from the customer origin only for customer-owned
  resource families and from the panel origin only for staff-owned resource
  families. The `/external/**` External Integration API and internal management
  endpoints such as Actuator do not receive a browser CORS policy.
* Browser resource families use `/api/{resource-family}/v1`; external resource
  families use `/api/external/{resource-family}/v1`. Location identifiers
  remain in validated bodies or query parameters rather than nested resource
  paths.
* The Next.js services render frontend concerns only; the Spring API owns API
  security, scheduled webhook delivery, and customer-push delivery.
* The HTTP topology is local-development-only. A public deployment still
  requires TLS and the non-bypassable gateway described in the security
  roadmap.

### 7.1 Current HTTP resource families

The implemented browser-facing contract consists of:

```text
GET    /api/auth/v1/csrf
POST   /api/auth/v1/login
POST   /api/auth/v1/refresh
POST   /api/auth/v1/logout
POST   /api/auth/v1/logout-all
GET    /api/auth/v1/me
POST   /api/tenant-registrations/v1

GET    /api/locations/v1
POST   /api/accounts/v1
PATCH  /api/accounts/v1/{accountId}/status

GET    /api/orders/v1
POST   /api/orders/v1
PUT    /api/orders/v1/{orderId}/status

GET    /api/tracked-orders/v1/{trackingReference}
GET    /api/tracked-orders/v1/{trackingReference}/events

GET    /api/customer-notifications/v1/configuration
PUT    /api/customer-notifications/v1/subscription
POST   /api/customer-notifications/v1/subscription-replacement
DELETE /api/customer-notifications/v1/subscription
DELETE /api/customer-notifications/v1/enrollments
```

The authenticated administrator management families are
`/api/external-integrations/v1`, `/api/api-keys/v1`,
`/api/api-key-versions/v1`, `/api/webhook-subscriptions/v1`, and
`/api/webhook-signing-secrets/v1`. Their lifecycle operations use the flat
resource-family convention and never accept client-supplied tenant ownership.

The implemented External Integration order contract is:

```text
GET    /api/external/orders/v1
GET    /api/external/orders/v1/{orderId}
POST   /api/external/orders/v1
PUT    /api/external/orders/v1/{orderId}/status
```

Staff order listing accepts an optional `locationId` and active `status`.
External listing accepts an opaque cursor plus optional authorized
`locationId` and `status`. Order creation carries `locationId` and an optional
custom label in its validated body. External creation additionally requires
`Idempotency-Key`. Desired-state updates use idempotent `PUT`; a same-state
request returns the unchanged representation without another history, customer
event, or outbox event.

Docker Compose provides a production-like local environment for both frontends,
the API, PostgreSQL, and Redis. It builds
immutable production-mode application images and does not synchronize source
files or run Fast Refresh or Spring Boot DevTools. Both Next.js applications run
their standalone build output, and the customer build generates the Serwist
service worker before the runtime image is assembled. The packaged Spring Boot
API runs Flyway migrations and scheduled webhook and customer-push background
jobs in the same application process. Every service remains on the Compose
network. The customer application, staff panel, API, PostgreSQL, and Redis
publish their development ports on every host interface. Applying source changes
requires rebuilding and recreating the affected application container.

The customer Next.js application serves the generated service worker with a
root scope, JavaScript content type, restrictive content-security policy, and
explicit no-cache headers so update checks do not reuse a stale script.

The Spring API has an explicit `staging` profile for disposable private VPS
staging. Activating it requires distinct customer, panel, and API HTTPS origins,
a JWT issuer equal to the API origin, a real VAPID mail contact,
non-development PostgreSQL and Redis credentials, and
externally mounted key files. The shared application configuration uses the
same environment-variable names and `/run/secrets` paths in every environment;
the staging profile adds validation and binds the Redis credentials that local
Redis does not require rather than redefining shared values. It requires the
public-HTTPS webhook and Customer Push policies and rejects local or placeholder
origins and identities, packaged key resources, development credentials, and
relaxed delivery policy. Secure, host-only `SameSite=Lax` cookie behavior
remains a non-configurable application invariant. The complete environment-
variable surface is recorded once in `.env.example`; the shared VPS Compose
topology and secret mounts remain separate deployment work.

## 8. Resilience and Consistency

* Order transitions and their history are committed atomically.
* Concurrent commands for the same order are serialized before validating and persisting a transition, preventing stale state decisions without duplicating an application-managed version value.
* Order transitions and required outbox events are committed atomically.
* SSE or Redis event loss does not prevent later REST recovery.
* Redis unavailability must not corrupt PostgreSQL state; event delivery may be delayed and recovered according to operational policy.
* A known webhook delivery outcome is attempted once and stored as success or a terminal dead-letter result; v1 has no policy retry or `Retry-After` handling.
* Crash recovery may repeat an uncertain webhook attempt, and repeated external client commands are handled safely where an integration can legitimately retry.
* Customer-push fan-out and delivery failure cannot roll back a committed order
  transition or block webhook fan-out.
* Customer-push delivery has a ten-minute freshness deadline and at most eight
  attempts. Transient network failures, `408`, `425`, `429`, and `5xx` use full
  jitter with a five-second exponential base capped at two minutes and honor a
  valid earlier `Retry-After` time. Other `4xx` responses terminate only the
  delivery; `404` and `410` also retire the complete subscription and its
  enrollments.
* A push is revalidated against the authoritative order immediately before
  submission. A queued notification that no longer represents the current
  order state is superseded rather than sent. Uncertain crash recovery may
  duplicate an accepted push, so the client deduplicates by stable event ID.
* Accepted, superseded, or subscription-canceled push rows are retained for
  seven days by default; expired and terminal dead-letter rows are retained for
  30 days. Unenrolled dormant subscriptions are removed after 30 days by
  default.
* An order remains associated with its original location for its entire lifecycle and history.
* Terminal orders remain readable to the holder of the tracking reference according to the configured retention policy but cannot re-enter an active lifecycle.

## 9. Verification and Acceptance Criteria

* Both frontends build and lint independently.
* Each frontend's handwritten request code and response types match the REST behavior covered by integration tests during the walking vertical slice.
* REST-backed Client Components use keyed SWR state rather than effects for request orchestration, retain cached data during background revalidation, and do not apply order transitions before the Spring API accepts them.
* New orders start in `IN_PREPARATION`, receive an immutable label, and create exactly one initial history entry for that resulting state.
* Automatic labels use the one-based count of all orders at the location for the UTC date under concurrent creation; custom labels are validated, advance that ordinal, and may duplicate existing labels.
* Staff order-list operations return only `IN_PREPARATION` and `READY` orders.
* Local login, invalid credentials, token expiry, refresh rotation, logout, and cookie/CSRF behavior are covered by tests.
* Anonymous tenant registration requires CSRF, creates exactly one related tenant, location, and active administrator in one transaction, requires and normalizes the administrator email, and creates no session.
* Registration conflicts roll back the tenant and location, and successful registration returns to sign-in without automatic authentication.
* When implemented, OAuth2/OIDC login creates or links the correct account and
  tenant ownership without deriving authorization from provider email.
* A tenant administrator can access all locations in the tenant but none in another tenant.
* A location manager can access and manage orders only in its assigned location and can provision operator accounts only for that location.
* A location operator can list, create, read, and update orders only in its assigned location and cannot provision accounts.
* Multiple panel devices at one location can use separate operator accounts with independently revocable credentials and sessions.
* Location-scoped API Keys cannot access an unassigned location, and direct order lookup outside a key's grants does not disclose that the order exists.
* Unauthenticated, unauthorized, cross-tenant, and cross-location staff or External Integration access is rejected, including when accessing data directly through repositories protected by RLS.
* Customer tracking works without an account, receives validated SSE invalidations only for the possessed tracking reference, and reconciles through REST after events and reconnects.
* The customer application exposes a valid globally branded manifest and the
  required regular, maskable, Apple touch, and favicon assets supplied for the
  project.
* Android Chrome and iOS Safari can install the customer application and launch
  it in standalone mode without changing the ordinary desktop-browser
  experience.
* Installing from an order page opens that order once on the first installed
  launch while preserving one stable Kairos application identity.
* Each browser or installed-app context independently retains active, explicit
  IndexedDB order snapshots in most-recently-opened order and removes terminal
  orders without allowing a stale response to resurrect them.
* Home renders local active-order summaries without opening SSE, opens the
  latest locally active order on subsequent installed launches, tolerates
  unavailable or invalid local storage, and can clear the collection and its
  backend enrollments after confirmation.
* The generated service worker precaches only the application shell and offline
  route, applies `NetworkOnly` to tracked-order REST, and shows an explicitly
  labeled last-known IndexedDB snapshot when navigation or REST is unavailable.
* Notification permission is requested only from a direct user action; iOS and
  iPadOS receive an Add to Home Screen explanation before any unsupported
  request, and denied permission receives browser-settings guidance.
* Enabling notifications reconciles one complete browser subscription with
  every locally active order, automatically enrolls later active orders,
  replaces browser-rotated subscriptions idempotently, and enforces ten
  subscription contexts per order.
* Disabling notifications removes the complete backend subscription before the
  browser subscription. Clearing local orders removes their enrollments while
  preserving the app-level preference. Neither flow reports success while
  offline.
* `READY`, `COMPLETED`, and `CANCELED` generate privacy-minimal versioned push
  payloads. The customer service worker validates the payload, deduplicates
  stable event IDs, enforces reachable state transitions, replaces older
  notifications for the same order, updates the active-enrollment badge, and
  opens or focuses the corresponding route.
* Web Push encryption, VAPID headers, public-endpoint enforcement, result
  classification, freshness, jittered retry, `Retry-After`, permanent
  retirement, and retention behavior are covered by protocol-level and
  application-level tests and by current-device acceptance on the required
  browsers before public deployment.
* A valid transition from either the staff panel or an External Integration produces the same persisted state, history record, customer event, and transactional outbox event.
* Integration, API Key, API Key Version, webhook subscription, and signing-secret lifecycle changes preserve one-time secret handling and historical audit attribution.
* Exact idempotent creation replays and same-state status commands create no duplicate order, history, outbox, or customer event.
* Webhook delivery failure cannot roll back or corrupt the committed order transition and is retained as a terminal dead-letter record.
* The REST and webhook contracts remain language-agnostic.

## 10. Current Delivery Status and Roadmap

The current walking vertical slice is implemented for local development:

* persisted labeled-order creation and controlled transitions;
* authenticated, tenant- and location-authorized staff operations;
* public tenant onboarding and scoped manager/operator provisioning;
* on-screen customer QR codes and anonymous tracking through REST;
* customer-only SSE invalidation through Redis Pub/Sub with REST
  reconciliation;
* customer PWA manifest, order-aware first-launch, active-only IndexedDB
  snapshots, generated Serwist service worker, explicit offline fallback,
  app-level notification consent and controls, application badges, and
  monotonic privacy-preserving push handling, with the final regular, maskable,
  Apple-touch, and favicon assets supplied and device acceptance still
  outstanding;
* administrator-managed External Integrations, API Keys, and webhook
  subscriptions;
* versioned external order commands with idempotent creation and desired-state
  updates;
* one channel-neutral transactional order outbox with Spring API background
  jobs for independent single-attempt webhook and durable retrying Web Push
  delivery;
* a fail-fast private-staging API configuration contract with exact browser
  CORS families, HTTPS environment identities, secure cookie policy,
  public-HTTPS delivery policy, externally mounted key paths, and independent
  PostgreSQL and Redis credentials.

The panel removes terminal orders from the active queue after an accepted
transition and shows the customer QR code without a separate tracking-link,
printing, or download workflow. The database starts empty. Because the local
database was discarded while developing these increments, schema changes are
consolidated in the initial Flyway migration rather than compatibility
migrations.

The customer PWA now supplies the referenced 192×192 and 512×512 regular
icons, 512×512 maskable icon, 180×180 Apple touch icon, and multi-resolution
favicon. Completing installability acceptance requires checking installation
and standalone launch behavior on Android Chrome and iOS Safari. The manifest
and launch behavior must not be redesigned while completing that acceptance
gap.

The next External Integration increment publishes an OpenAPI document, rendered
public reference documentation, and formal automated public-contract checks.
The handwritten frontend clients remain in place; generated SDKs are a later,
separate decision.

The later authentication increment adds OAuth2/OIDC provider configuration,
explicit identity linking, callbacks, and the same Kairos session issuance and
authorization model used by local login. Linking must validate state, nonce,
issuer, audience, and the provider's immutable subject. Whether linking begins
from an authenticated account or a future administrator-created setup flow
remains an open product decision.

Before any public deployment:

* establish a verified tenant and location database security context and enable
  PostgreSQL Row Level Security for every tenant-owned or ownership-derived
  table;
* introduce a non-bypassable API gateway that preserves a trustworthy client
  address and rate-limits login, refresh, tenant registration, External
  Integration access, and future recovery or linking routes;
* provide externally managed JWT signing keys, webhook-secret encryption keys,
  VAPID signing keys, and push-subscription encryption keys, with documented
  rotation procedures, security-event retention, monitoring, and dependency
  patching;
* complete Android Chrome, iOS/iPadOS Safari, desktop Chrome, and desktop Firefox
  service-worker, offline, subscription, notification, click, badge, and
  subscription-replacement acceptance.

Deferred operational and product work includes live staff queue
synchronization, order archives and search, printable QR artifacts,
cancellation confirmation, tracking-reference expiration, account listing and
session-management UI, additional administrators and locations, invitations,
setup links, email verification, recovery, CAPTCHA, MFA, passkeys, webhook DLQ
inspection and alerts, automatic webhook retry or redelivery, strict delivery
ordering, application-owned install prompts, and native mobile variants. Any of
these requires an explicitly approved increment and synchronized changes to
this document.
