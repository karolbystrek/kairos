# Kairos Architecture and Requirements

## 1. Purpose and Scope

Kairos is a multi-tenant virtual pager system for restaurants. A tenant represents a customer organization, such as an independent restaurant or restaurant chain, and owns one or more physical locations. A customer scans a QR code assigned to an order and opens a lightweight web application that displays the current order state and receives real-time updates. Restaurant staff manage orders through a separate administrative panel. External point-of-sale systems can create and update orders through a documented REST API and receive webhooks.

The core system consists of three independently deployable applications:

* **Customer application:** anonymous, mobile-first Next.js PWA for order tracking.
* **Staff panel:** authenticated Next.js application for queue management and QR-code generation.
* **API:** Spring Boot application responsible for all business rules, security, persistence, real-time communication, and external integrations.

Native mobile applications, App Clips, Instant Apps, and Web Push may be added later, but the browser experience must work without installing an application.

## 2. Technology and Ownership Decisions

### 2.1 Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, and HeroUI 3. They remain separate because they serve different audiences and have different authentication, PWA, caching, and release concerns.

HeroUI is the component library for both applications. Zod validates frontend-owned form input and real-time event payloads. Native `fetch` is the default HTTP client. SWR may be used in the staff panel where polling, caching, or client-side revalidation improves queue synchronization.

The frontends must not contain domain authority. Hiding a control or redirecting an unauthenticated user does not replace backend authorization.

### 2.2 Backend

The backend uses Java 25 and Spring Boot 4. It is the only system of record and owns:

* order lifecycle and transition rules;
* staff authentication and authorization;
* tenant, location, and staff-access resolution;
* PostgreSQL persistence and migrations;
* WebSocket/STOMP publication;
* Redis-backed coordination and pub/sub;
* POS authentication, REST integration, and webhooks;
* transactional outbox processing.

Next.js route handlers must not become a parallel domain API or backend-for-frontend layer.

### 2.3 API contracts

REST is the public integration contract for frontends and external POS systems. The Spring API exposes an OpenAPI 3 description using a Spring Boot 4-compatible version of springdoc-openapi.

Each frontend generates its own `typescript-fetch` client from the same OpenAPI document. Generated models and operations are the source of frontend REST types; equivalent request and response DTOs must not be maintained manually. CI must detect when generated clients no longer match the API contract.

## 3. Functional Requirements

### 3.1 Order lifecycle

An order follows a controlled lifecycle:

1. **Created:** staff or an authorized POS creates an order for an accessible location and receives a QR-code tracking reference.
2. **In preparation:** the customer starts tracking and the order is being prepared.
3. **Ready for pickup:** staff or the POS marks the order ready; connected customers receive an immediate event and the browser attempts vibration or sound when permitted.
4. **Completed:** the order is collected, the live session ends, and later scans show a terminal state.
5. **Canceled:** the order is canceled, connected customers are informed, and the live session ends.

The backend validates every transition regardless of whether it originates from the panel or a POS. Every accepted transition is recorded in an append-only history.

### 3.2 Customer application

The customer application must:

* open directly from an order-specific QR code without login or installation;
* display the current order state before establishing real-time communication;
* subscribe only to updates for the referenced order;
* validate every received event before updating the interface;
* reconnect with bounded exponential backoff after connectivity loss;
* fetch the current state through REST after reconnecting to recover missed updates;
* provide clear terminal views for completed, canceled, expired, or unknown orders;
* use Wake Lock, vibration, and audio only when supported and permitted;
* remain usable when optional browser APIs or PWA installation are unavailable.

Browser suspension may interrupt WebSocket delivery. The application must reconcile through REST when it becomes active again and must not promise reliable background delivery without a future push or native notification mechanism.

### 3.3 Staff panel

The staff panel must:

* require an authenticated staff identity;
* show only locations and orders accessible to the staff member;
* allow authorized chain-level staff to switch between locations or view an aggregate queue;
* create orders for an accessible location and produce customer QR codes;
* display and refresh active order queues by location;
* allow only valid order transitions permitted by the staff member's role;
* provide clear feedback for stale data, rejected transitions, expired sessions, and network failures.

### 3.4 POS API and webhooks

The public POS API must remain usable by clients written in any language. An authorized POS can create orders, read relevant order state, and request valid transitions only for locations included in its credential scope. A chain-wide integration may be granted multiple locations when required.

Order changes that require an outbound notification create an outbox record in the same database transaction. A separate worker delivers webhooks, retries transient failures with backoff, and records terminal delivery failure without rolling back the original order transition. Webhook consumers must be able to handle duplicate delivery safely.

## 4. Authentication and Security

### 4.1 Staff authentication

Spring Security is the sole authentication authority.

The system supports:

* Kairos-managed staff accounts with BCrypt-hashed passwords;
* OAuth2/OIDC login, initially demonstrated with Google but configurable for another provider;
* linking an external provider identity to a Kairos staff account and tenant membership;
* the same application authorization model regardless of login method.

After either login method, Spring issues a short-lived signed access JWT and a rotating refresh credential. Browser credentials are transported in `Secure`, `HttpOnly`, `SameSite=Lax`, host-only cookies through the staff-panel origin. Cookie-authenticated state-changing requests require CSRF protection.

The authenticated principal carries the staff identity, active tenant, and tenant-level roles needed for authorization. Location assignments are resolved by the backend rather than embedded as a potentially large or stale list in the JWT. The API derives tenant and location access from authenticated memberships and never trusts a tenant or location identifier merely because it was supplied by the client.

The API provides operations for local login, session refresh, logout, and retrieving the current staff identity, together with OAuth2/OIDC initiation and callback handling. Exact URL and payload naming belongs to the API design and OpenAPI contract rather than this requirements document.

### 4.2 Customer access

Customer order tracking is anonymous. QR codes use high-entropy, unguessable order references and expose only the minimum customer-facing order information. Possession of a tracking reference grants read-only access to that order and never authorizes staff operations or access to tenant data.

### 4.3 POS access

POS integrations authenticate with a bearer API key. Only a non-reversible hash is stored. Keys belong to one tenant and are scoped to one or more of its locations. They can be revoked or rotated and never authorize another tenant or an unassigned location.

### 4.4 Tenant isolation

All tenant-owned data is protected at both application and database levels. PostgreSQL Row Level Security provides defense in depth. Every transaction accessing tenant-owned rows must establish tenant and location access from a verified staff membership or POS credential.

Orders derive tenant ownership through their physical location rather than storing a second direct tenant relationship. RLS policies for orders and their dependent records must verify the location's tenant and, for staff or POS operations, the principal's access to that location. A location cannot be reassigned to another tenant after operational data has been created; such a change requires an explicit migration.

## 5. Persistence Requirements

The database schema must include tables covering the following concepts. Names and nonessential columns are implementation decisions.

* **Tenants:** stable identity, display information, and integration configuration.
* **Locations:** physical restaurant belonging to one tenant, with display and operational information.
* **Staff accounts:** stable identity, local credential hash when applicable, and account state.
* **External identities:** provider and immutable provider subject linked to a staff account.
* **Tenant memberships:** relationship between a staff account and tenant, including authorization roles.
* **Location access:** relationship between a tenant membership and each accessible location, including location-specific roles where needed.
* **Orders:** public tracking identity, owning location, customer-facing label, current state, and lifecycle timestamps. Tenant ownership is derived through the location.
* **Order history:** order association, state transition, time, and transition source or actor; records are append-only.
* **Refresh sessions:** staff/session association, hashed rotating credential, expiry, and revocation state.
* **POS credentials:** tenant association, hashed API key material, and credential lifecycle state.
* **POS location access:** relationship between a POS credential and each permitted location.
* **Outbox events:** aggregate association, event payload or type, delivery state, and retry scheduling information. Tenant or location information may be captured in the event envelope when required for reliable delivery, but it is not the canonical ownership relationship for an order.

Tenant ownership may be direct or derived through an unambiguous relationship such as order to location to tenant. Tables must carry enough association for RLS enforcement and efficient access checks without duplicating ownership data by default. Secrets, passwords, refresh credentials, and API keys must never be stored in plaintext.

## 6. Real-Time Communication

The API publishes compact order-state events through STOMP over WebSocket. Events contain only the information required for the customer to recognize the order, new state, and event time. The customer validates the event shape with Zod before use.

Redis pub/sub distributes events between API instances so a client receives updates regardless of which instance processed the transition. Redis may also support WebSocket coordination, but PostgreSQL remains the source of truth. Clients always use REST reconciliation after reconnecting instead of treating Redis or the WebSocket stream as durable history.

Customer subscriptions are read-only and scoped to one order reference. Staff and administrative real-time channels, if added, require the same backend tenant and location authorization as REST operations.

## 7. Routing and Deployment

Caddy terminates TLS and routes the independently deployable services.

* The customer and staff applications retain separate origins.
* Browser-facing REST, WebSocket, and OAuth paths are proxied through the relevant frontend origin to avoid an unnecessary cross-origin browser architecture.
* The dedicated API origin remains available for external POS integrations and webhook-related API access.
* The Next.js services render frontend concerns only; Caddy proxying does not make them owners of API security.

Docker Compose provides the local environment for both frontends, the API, PostgreSQL, Redis, and Caddy.

## 8. Resilience and Consistency

* Order transitions and their history are committed atomically.
* Order transitions and required outbox events are committed atomically.
* WebSocket loss does not prevent later REST recovery.
* Redis unavailability must not corrupt PostgreSQL state; event delivery may be delayed and recovered according to operational policy.
* Webhook retries use bounded backoff and distinguish retryable failures from terminal failures.
* Repeated webhook delivery and repeated client commands are handled safely where an integration can legitimately retry.
* An order remains associated with its original location for its entire lifecycle and history.
* Terminal orders remain readable to the holder of the tracking reference according to the configured retention policy but cannot re-enter an active lifecycle.

## 9. Verification and Acceptance Criteria

* Both frontends build and lint independently without tRPC or NextAuth.js.
* Generated TypeScript clients match the published OpenAPI contract.
* Local login, invalid credentials, token expiry, refresh rotation, logout, and cookie/CSRF behavior are covered by tests.
* OAuth2/OIDC login creates or links the correct staff identity and tenant membership.
* A tenant administrator can access all locations in the tenant but none in another tenant.
* Location staff can list, create, read, and update orders only in assigned locations; staff assigned to multiple locations can access each assignment.
* Location-scoped POS credentials cannot access an unassigned location.
* Unauthenticated, unauthorized, cross-tenant, and cross-location REST and WebSocket access is rejected, including when accessing data directly through repositories protected by RLS.
* Customer tracking works without an account, receives validated live updates, and reconciles correctly after reconnecting.
* A valid transition from either the staff panel or POS produces the same persisted state, history record, and customer event.
* Outbox delivery retries failures without losing the committed order transition.
* The REST and webhook contracts remain consumable without a TypeScript-specific client.
