# Kairos Architecture and Requirements

## 1. Purpose and Scope

Kairos is a multi-tenant virtual pager system for restaurants. A tenant represents a customer organization, such as an independent restaurant or restaurant chain, and owns one or more physical locations. A customer scans a QR code assigned to an order and opens a lightweight web application that displays the current order state and receives real-time updates. Restaurant staff manage orders through a separate administrative panel. External systems, initially point-of-sale systems, can create and update orders through a versioned REST API and receive webhooks.

The core system consists of three independently deployable applications:

* **Customer application:** anonymous, mobile-first Next.js PWA for order tracking.
* **Staff panel:** authenticated Next.js application for queue management and QR-code generation.
* **API:** Spring Boot application responsible for all business rules, security, persistence, real-time communication, and external integrations.

Native mobile applications, App Clips, Instant Apps, and Web Push may be added later, but the browser experience must work without installing an application.

## 2. Technology and Ownership Decisions

### 2.1 Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, and HeroUI 3. They remain separate because they serve different audiences and have different authentication, PWA, caching, and release concerns.

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
* transactional outbox processing.

Browser-facing API requests are routed through Caddy to the Spring API. Next.js route handlers are limited to frontend rendering concerns.

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
* provide clear terminal views for completed, canceled, or unknown orders;
* keep completed and canceled tracking references readable without an expiration policy in the current scope.

The current customer interface only refreshes the visible state. Wake Lock, vibration, audio, offline installation behavior, and other PWA notification features are deferred to a later frontend effort. Browser suspension may interrupt SSE delivery, so the application reconciles through REST when it becomes active again and does not promise reliable background delivery.

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
* provide clear feedback for stale data, rejected transitions, expired sessions, and network failures.

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

Order changes create an immutable outbox event in the same database transaction
as the order and history mutation. A separate worker fans out recipient-specific
delivery rows and attempts each delivery once at the application-policy level.
Any redirect, timeout, network failure, or non-`2xx` response is durably
dead-lettered; v1 performs no automatic delivery retry. Worker crash recovery
may result in duplicate delivery, so consumers deduplicate by the stable
CloudEvent ID and reject stale order snapshots by state and timestamp.

Production webhook destinations require HTTPS and public network addresses.
DNS is revalidated for delivery, redirects are not followed, and connection,
response, and response-body handling are bounded. Only an operator-controlled
local profile may relax HTTP and private-address restrictions.

## 4. Authentication and Security

### 4.1 Staff authentication

Spring Security is the sole authentication authority.

The system supports:

* Kairos-managed accounts with normalized usernames and BCrypt-hashed passwords;
* OAuth2/OIDC login, initially demonstrated with Google but configurable for another provider;
* linking an external provider identity to a Kairos account and its owning tenant;
* the same application authorization model regardless of login method.

After either login method, Spring issues a short-lived signed access JWT and a rotating refresh credential. Browser credentials are transported in `Secure`, `HttpOnly`, `SameSite=Lax`, host-only cookies through the staff-panel origin. Cookie-authenticated state-changing requests require CSRF protection.

Public tenant onboarding is the only anonymous account-creation flow. It atomically creates one tenant, its first location, and its first active administrator. The administrator requires a normalized, globally unique email address in addition to its normalized username and BCrypt-hashed password. Registration does not issue authentication cookies or sign the administrator in. During rapid development, tenants, locations, accounts, and orders have no separate display-name fields; accounts are presented by username and the other concepts by their full stable identifiers.

Standalone accounts are provisioned rather than self-registered. Each account belongs directly to one tenant. A tenant administrator has tenant-wide access; a location manager or operator has at most one location assignment. Location operator accounts are device-oriented, and a location uses a separate account for each panel device that needs independent credentials or revocation. Tenant administrators may provision managers and operators, while location managers may provision only operators for their own location.

The tenant-registration endpoint is anonymous but retains normal CSRF protection because it is invoked from the browser panel origin. The current local slice does not implement application-level rate limiting for tenant registration, login, or refresh. Before public deployment, a dedicated API gateway in front of Spring must enforce request throttling for these routes, preserve a trustworthy client-address boundary, and prevent direct access that bypasses the gateway. Spring remains the authentication authority and does not duplicate the gateway's request-rate counters.

One browser profile on the staff-panel origin represents one signed-in account because its tabs share the same authentication cookies. Same-account tabs may be used on a best-effort basis, but the panel does not promise immediate cross-tab interface synchronization. Different accounts in different tabs of one browser profile are unsupported; simultaneous accounts require separate browser profiles, private browsing contexts, or devices. The panel provides one primary operational workspace, including administrator location switching and aggregate views, rather than depending on browser tabs for core workflows.

The authenticated principal carries the account identity, owning tenant, and tenant-level role needed for authorization. The backend resolves the current location assignment rather than embedding mutable assignment data in the JWT. The API derives tenant and location access from the authenticated account and never trusts a tenant or location identifier merely because it was supplied by the client.

The API provides operations for local login, session refresh, logout, and retrieving the current account identity, together with OAuth2/OIDC initiation and callback handling. Exact URL and payload naming belongs to the later API design rather than this requirements document.

### 4.2 Customer access

Customer order tracking is anonymous. QR codes use high-entropy, unguessable order references and expose only the minimum customer-facing order information. Possession of a tracking reference grants read-only access to that order and never authorizes staff operations or access to tenant data.

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
* **Outbox events:** immutable event identity, order/location association, exact serialized CloudEvent payload, occurrence time, and fan-out state.
* **Webhook deliveries:** recipient-specific captured destination, payload, signing versions, claim state, one attempt outcome, bounded response details, and durable success or dead-letter state.
* **External order creation identity:** integration-and-location-scoped idempotency value and canonical creation-input fingerprint associated with the created order.

Tenant ownership may be direct or derived through an unambiguous relationship such as order to location to tenant. Tables must carry enough association for RLS enforcement and efficient access checks without duplicating ownership data by default. Secrets, passwords, refresh credentials, and API keys must never be stored in plaintext.

Order labels are trimmed, single-line text with a maximum of 32 characters. Automatic labeling is requested by omitting the custom label and uses the order's one-based creation ordinal among all orders at its location during the current UTC date. A provided blank label is invalid. Labels preserve casing, may contain ordinary Unicode text, and are not unique; staff remain responsible for avoiding ambiguous duplicates. Labels cannot be edited after order creation.

## 6. Real-Time Communication

The API exposes a same-origin Server-Sent Events stream for each active customer tracking reference. The stream is anonymous and read-only: possession of the high-entropy tracking reference grants access to that order's events but never authorizes a command. The customer validates each compact event with Zod and treats it only as an invalidation signal; SWR then retrieves the authoritative customer representation through REST.

Order-state events are published only after the PostgreSQL transition and history transaction commits. Redis Pub/Sub distributes each event to every live API instance, and each instance forwards it to its locally connected SSE clients for that tracking reference. Publishing and subscription are mandatory application behavior and have no feature flag. Redis remains part of application health reporting and its health contributor must not be disabled. The publishing instance must not turn a committed transition into a failed command response when Redis is unavailable.

Redis Pub/Sub and SSE are intentionally non-durable. The client reconciles through REST when the stream opens or reopens, on focus, and after browser connectivity returns. Cache invalidation may clear the internal SWR entry before refetching, but the customer UI retains the last authoritative order during that request so the active stream is not torn down and reopened. While SSE is disconnected it falls back to approximately 15-second REST polling. No periodic safety request runs while SSE appears healthy, so the current increment accepts the rare possibility that an after-commit Redis publication failure leaves a page stale until another reconciliation trigger.

A terminal transition produces the final invalidation and ends the live stream. Opening an already terminal order returns its REST state without maintaining an SSE connection. Servlet async and error redispatches continue processing the authorization decision made for the original request instead of being treated as new protected commands. Customer live delivery is the only real-time browser channel in this increment; authenticated staff queue streaming is deferred. Future genuinely bidirectional features may introduce WebSocket independently rather than changing this SSE contract.

## 7. Routing and Deployment

Caddy terminates TLS and routes the independently deployable services.

* The customer and staff applications retain separate origins.
* Browser-facing REST, SSE, and OAuth paths are proxied through the relevant frontend origin to avoid an unnecessary cross-origin browser architecture.
* Browser resource families use `/api/{resource-family}/v1`; external resource families use `/api/external/{resource-family}/v1`. Location identifiers remain in validated bodies or query parameters rather than nested resource paths.
* The dedicated API origin remains available for External Integration REST access. Webhook delivery originates from the separate worker and has no inbound worker endpoint.
* The Next.js services render frontend concerns only; the Spring API owns API security.

Docker Compose provides the local environment for both frontends, the API,
separate webhook worker, PostgreSQL, Redis, and Caddy. API and worker modes are
built from the same Spring Boot codebase and image. The worker exposes no
business endpoint and can scale independently.

## 8. Resilience and Consistency

* Order transitions and their history are committed atomically.
* Concurrent commands for the same order are serialized before validating and persisting a transition, preventing stale state decisions without duplicating an application-managed version value.
* Order transitions and required outbox events are committed atomically.
* SSE or Redis event loss does not prevent later REST recovery.
* Redis unavailability must not corrupt PostgreSQL state; event delivery may be delayed and recovered according to operational policy.
* A known webhook delivery outcome is attempted once and stored as success or a terminal dead-letter result; v1 has no policy retry or `Retry-After` handling.
* Crash recovery may repeat an uncertain webhook attempt, and repeated external client commands are handled safely where an integration can legitimately retry.
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
* OAuth2/OIDC login creates or links the correct account and tenant ownership.
* A tenant administrator can access all locations in the tenant but none in another tenant.
* A location manager can access and manage orders only in its assigned location and can provision operator accounts only for that location.
* A location operator can list, create, read, and update orders only in its assigned location and cannot provision accounts.
* Multiple panel devices at one location can use separate operator accounts with independently revocable credentials and sessions.
* Location-scoped API Keys cannot access an unassigned location, and direct order lookup outside a key's grants does not disclose that the order exists.
* Unauthenticated, unauthorized, cross-tenant, and cross-location staff or External Integration access is rejected, including when accessing data directly through repositories protected by RLS.
* Customer tracking works without an account, receives validated SSE invalidations only for the possessed tracking reference, and reconciles through REST after events and reconnects.
* A valid transition from either the staff panel or an External Integration produces the same persisted state, history record, customer event, and transactional outbox event.
* Integration, API Key, API Key Version, webhook subscription, and signing-secret lifecycle changes preserve one-time secret handling and historical audit attribution.
* Exact idempotent creation replays and same-state status commands create no duplicate order, history, outbox, or customer event.
* Webhook delivery failure cannot roll back or corrupt the committed order transition and is retained as a terminal dead-letter record.
* The REST and webhook contracts remain language-agnostic.

## 10. Incremental Delivery

The first walking vertical slice is intentionally limited to local development. Its main-flow increment provides persisted labeled-order creation and transitions in the staff panel, on-screen QR-code generation, anonymous customer state retrieval through REST, and customer-only SSE invalidation through Redis Pub/Sub. The database starts empty; tenants and locations are not seeded by production migrations.

The original walking slice temporarily exposed unauthenticated order-management endpoints. The backend authentication increment now protects staff operations with provisioned local accounts, tenant/location authorization, CSRF, and trusted authenticated audit identity. The panel frontend now provides local login, current-account loading, logout, automatic CSRF headers, and one refresh plus request retry after an expired access credential. Cooperating same-origin tabs serialize refresh through a browser Web Lock and recheck the current session after acquiring it, avoiding a duplicate rotation without maintaining a persistent cross-tab state machine. Authentication credentials remain in secure `HttpOnly` cookies and are not stored by the frontend. The main-flow increment replaces the temporary manual-only customer refresh with SSE invalidation plus REST reconciliation; SWR continues to call handwritten native `fetch` request modules rather than OpenAPI tooling or generated clients. PostgreSQL RLS enforcement remains required before deployment. The external contract is now versioned and language-agnostic; OpenAPI publication, rendered reference documentation, generated SDKs, and formal automated public-contract checks are deferred to the next increment.

The in-process authentication rate limiter was removed from the local slice. Login, refresh, and tenant registration are currently not request-rate-limited. A later production ingress increment introduces a dedicated API gateway for route- and client-level throttling before these endpoints are exposed publicly; the deployment must make the gateway non-bypassable.

The main-flow increment originally covered staff-panel creation and transitions
only. The subsequent External Integration increment adds administrator-managed
integrations, versioned API Keys, external order commands, webhook
subscriptions, transactional outbox fan-out, and the independently deployed
single-attempt delivery worker. Live staff queue synchronization, order
archives, label editing, printable QR artifacts, cancellation confirmation,
alerts, and tracking-reference expiration remain outside its scope. The panel
removes terminal orders from the active queue immediately after an accepted
transition. It shows only the QR code for customer access and does not add a
separate tracking-link, printing, or download workflow.

The local database was discarded before this increment, so its schema changes are consolidated into the existing initial Flyway migration rather than added as a compatibility migration. Backend and frontend automated tests are run only when explicitly requested; the implementation otherwise receives the repository's prescribed static checks.

The implementation sequences and verification checklists are maintained in [`ORDER_MAIN_FLOW_IMPLEMENTATION_PLAN.md`](ORDER_MAIN_FLOW_IMPLEMENTATION_PLAN.md) and [`EXTERNAL_INTEGRATION_IMPLEMENTATION_PLAN.md`](EXTERNAL_INTEGRATION_IMPLEMENTATION_PLAN.md).

The staff-authentication increment implements provisioned local username/password accounts, Kairos-issued access and refresh credentials, secure cookies, CSRF protection, logout, account and session revocation, tenant/location authorization, provisioning rules, and authenticated audit identity. OAuth2/OIDC login is delivered in a later increment after that local authentication and authorization foundation is complete. The earlier increment must nevertheless keep the account schema, external-identity boundary, authenticated principal, and session-issuance services compatible with the later OIDC method so that both login methods resolve to the same Kairos account and authorization model.

The panel now exposes public tenant onboarding and capability-gated account provisioning. Onboarding creates the first tenant administrator only; it is not general account self-registration. Authenticated administrators can provision managers and operators for accessible locations, while managers can provision only operators for their fixed location. Additional administrators, later location creation, account listing or status management, invitations, password setup links, email verification, recovery, and CAPTCHA remain outside this increment.
