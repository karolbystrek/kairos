# Order Main-Flow Implementation Plan

Status: approved for implementation

Decision date: 2026-07-24

## 1. Objective

Complete the first usable restaurant order flow:

1. Staff creates an order in the authenticated panel.
2. Spring immediately places the order in preparation and assigns its label.
3. The panel displays an order-specific QR code.
4. The customer opens the anonymous tracking page and sees the current label and state.
5. Staff changes the state through authenticated REST commands.
6. Redis and Server-Sent Events notify the customer page, which reloads the authoritative REST representation.
7. Completed or canceled orders leave the active staff queue but remain readable through their tracking links.

This plan implements the decisions in [`REQUIREMENTS.md`](REQUIREMENTS.md). If the plan and requirements diverge, update the requirements through discussion before changing behavior.

## 2. Scope

### Included

- Replace `CREATED` with immediate `IN_PREPARATION`.
- Preserve the existing authenticated staff authorization and audit identity.
- Add immutable customer-visible order labels.
- Provide automatic daily numbering and optional custom labels.
- Return only active orders in staff queue endpoints.
- Keep cancellation available from both active states.
- Add customer-only SSE invalidation.
- Fan out committed order events between API instances with Redis Pub/Sub.
- Reconcile customer state through REST after every valid SSE event.
- Poll REST while SSE is disconnected.
- Keep terminal tracking links readable without expiration.
- Consolidate schema changes into the existing Flyway `V1`.
- Add and run backend automated tests.
- Run static checks for both changed frontends without adding frontend tests.

### Excluded

- POS commands and credentials.
- Webhooks, transactional outbox processing, and durable event delivery.
- Authenticated staff SSE or cross-device live queue synchronization.
- Alerts, vibration, sound, Wake Lock, offline behavior, and other PWA work.
- QR printing, downloads, or a separate staff-side tracking-link action.
- Order archive and search.
- Label editing.
- Cancellation confirmation.
- Label uniqueness enforcement.
- Location-local numbering or configurable business-day boundaries.
- Tracking-link expiration.
- Frontend automated tests.

## 3. Accepted Behavior

### 3.1 Lifecycle

```text
IN_PREPARATION -> READY -> COMPLETED
       |            |
       +----------> CANCELED
```

- Creation persists `IN_PREPARATION` and one matching history entry in the same transaction.
- The customer never changes state.
- `COMPLETED` and `CANCELED` are terminal.
- Terminal orders remain available through anonymous tracking REST.
- Staff queue reads contain only `IN_PREPARATION` and `READY`.

### 3.2 Labels

- Every order has one immutable textual `label`.
- The panel defaults to **Auto** and offers **Custom** as the alternative.
- Omitting a label requests automatic allocation.
- Providing an empty or whitespace-only custom label is invalid.
- Custom labels are stripped of surrounding whitespace, must be single-line, contain no control characters, and contain at most 32 Unicode characters.
- Label casing is preserved.
- Labels may duplicate, including duplicates between custom and automatic labels.
- Automatic labels are decimal text such as `"1"`.
- Automatic labels are allocated independently for each location and UTC date.
- An automatic label is one greater than the count of all orders already created for that location during the UTC date.
- Custom-labeled orders advance the daily ordinal even though their label remains custom.
- Only the resulting text label is stored; no numbering date, numeric value, label source, or allocation state is persisted.

### 3.3 Time

- Add an IANA time-zone field to each location and initialize it to `UTC`.
- Do not use the location field for numbering yet.
- Derive the numbering date explicitly in UTC from the injected `Clock`.
- Keep `Clock.systemUTC()`.
- Hard-code the API/JVM, serialization, and database session time zone to UTC; do not add an environment-selectable application time-zone setting.

### 3.4 Live delivery

- The public edge transport is SSE, not STOMP or WebSocket.
- SSE is customer-only and scoped by the high-entropy tracking reference.
- Staff commands remain REST requests.
- An event is eligible for publication only after the order transition and history transaction commits.
- Redis Pub/Sub is best-effort and non-durable.
- Redis failure never rolls back a transition or changes a successful transition response into a failure.
- Each API instance forwards Redis messages to its own matching SSE connections.
- SSE data contains enough information to validate routing and change shape, but it is not used as authoritative order state.
- A valid event calls SWR revalidation; REST supplies the state displayed to the customer.
- A terminal transition sends the final invalidation and closes matching streams.
- A customer opening an already terminal order receives REST state without retaining an SSE stream.
- While SSE is disconnected, the customer polls REST approximately every 15 seconds.
- SSE open or reopen, page focus, and browser connectivity restoration trigger immediate REST revalidation.
- No safety polling runs while SSE appears healthy. The agreed rare missed-publication gap remains accepted.

## 4. Target Contracts

Exact response records remain small and handwritten.

### Create an order

`POST /api/locations/{locationId}/orders`

Automatic label:

```json
{}
```

Custom label:

```json
{
  "label": "Table 4"
}
```

The response remains the staff order representation and adds `label`. Its initial `status` is always `IN_PREPARATION`.

### Read active staff orders

- `GET /api/locations/{locationId}/orders`
- `GET /api/orders`

Both endpoints return only `IN_PREPARATION` and `READY` orders and include `label`.

### Change status

`PATCH /api/orders/{orderId}/status`

The request shape remains:

```json
{
  "status": "READY"
}
```

The accepted target is validated against the persisted state while holding the existing order row lock.

### Read customer state

`GET /api/tracked-orders/{trackingReference}`

Response:

```json
{
  "label": "12",
  "status": "READY",
  "updatedAt": "2026-07-24T12:00:00Z"
}
```

Update the customer request module to use this existing backend path instead of the stale `/api/order-tracking/...` path.

### Subscribe to changes

`GET /api/tracked-orders/{trackingReference}/events`

Response media type: `text/event-stream`

Event name: `order-status-changed`

Data:

```json
{
  "trackingReference": "00000000-0000-0000-0000-000000000000",
  "status": "READY",
  "updatedAt": "2026-07-24T12:00:00Z"
}
```

The customer validates all fields and verifies that `trackingReference` matches the open page before requesting current REST state.

## 5. Persistence Design

Rewrite `apps/api/src/main/resources/db/migration/V1__create_initial_schema.sql`; do not add `V2`.

### Locations

Add:

- `time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC'`
- a nonblank check

Set `Location.create(...)` explicitly to `UTC` even though the schema default protects direct fixture inserts.

### Orders

Add:

- `label VARCHAR(32) NOT NULL`

Add checks for:

- a nonblank, already-stripped label;
- status belonging to `IN_PREPARATION`, `READY`, `COMPLETED`, or `CANCELED`.

Do not add a uniqueness constraint for `label`.

### Automatic allocation

Use the location row as the serialization boundary:

1. Resolve staff access.
2. Load the accessible location with `PESSIMISTIC_WRITE`.
3. Derive half-open UTC-day bounds from the injected clock.
4. Count all orders for that location whose `created_at` is within those bounds.
5. Allocate the count plus one.
6. Persist its decimal string only as `label`.

Locking the location serializes concurrent creations for that location, while different locations remain independent. Querying all orders, including custom-labeled and terminal orders, makes the automatic label the order's daily creation ordinal.

There is no deletion or expiration in this increment. If retention later deletes orders, move the sequence state to a dedicated counter before allowing deletion to violate the no-reuse rule.

## 6. Backend Implementation

### Step 1: Schema, time, and domain model

- Update Flyway `V1` with location time zone and order-label fields.
- Remove `CREATED` from `OrderStatus`.
- Add `isActive()` and `isTerminal()` domain semantics rather than repeating status sets.
- Change `CustomerOrder.create(...)` to create `IN_PREPARATION` with an allocated label.
- Add the label mapping to `CustomerOrder`.
- Add the time-zone mapping to `Location`.
- Configure JDBC/Hibernate and JSON time handling explicitly for UTC.
- Fix the API container runtime time zone to UTC without introducing a configurable Kairos time-zone property.

### Step 2: Label input and allocation

- Add a `CreateOrderRequest` API record with an optional custom label.
- Validate custom labels with Jakarta Bean Validation at the API boundary.
- Normalize accepted custom labels once by stripping surrounding whitespace.
- Add a location repository method with a pessimistic write lock.
- Add a derived order repository count for a location and half-open `created_at` range.
- Keep allocation inside the same transaction that creates the order and its first history entry.
- Return clear `400 Bad Request` problem details for invalid custom labels.

### Step 3: Active queue and lifecycle contracts

- Change location and tenant order queries to filter active states in the database.
- Include `label` in `StaffOrderView`, `StaffOrderResponse`, `TrackedOrderView`, and `CustomerOrderResponse`.
- Update transition tests and authorization tests for the removed `CREATED` state.
- Preserve the existing order row lock before transition validation.
- Publish no creation event because no customer can possess the tracking reference before creation completes.

### Step 4: After-commit event boundary

- Define one application event record containing tracking reference, accepted status, and update time.
- Publish that application event from `OrderService.updateStatus(...)` after persistence work has been accepted in the transaction.
- Handle it with a transaction listener configured for `AFTER_COMMIT`.
- Serialize it as versioned JSON on one documented Redis channel.
- Install Redis publication and subscription unconditionally; do not add an application feature flag for order events.
- Catch and log Redis publication failures inside the after-commit adapter so they cannot escape as a false command failure.
- Do not add an outbox, retry worker, Redis Stream, or durable replay.

### Step 5: Redis-to-SSE bridge

- Configure a Redis message listener for the order-status channel.
- Validate/deserialise Redis messages before forwarding them.
- Maintain a concurrency-safe registry of multiple `SseEmitter` connections per tracking reference.
- Remove emitters on completion, timeout, and I/O failure.
- Send periodic SSE comment heartbeats to keep idle streams observable without causing REST revalidation.
- Forward one named invalidation event to all local emitters for the matching reference.
- After forwarding a terminal event, complete and remove those emitters.
- Treat duplicate events and duplicate browser connections as harmless.

### Step 6: Public SSE endpoint and security

- Add the SSE handler under `TrackedOrderController` or a cohesive adjacent controller.
- Confirm the tracking reference exists before retaining an emitter.
- Return `404` for an unknown reference.
- Avoid retaining a stream for an already terminal order.
- Register an active emitter and flush an initial connection event or comment so the browser can enter the open state.
- Permit anonymous `GET /tracked-orders/**` access through the existing security rule.
- Keep the cookie bearer-token resolver from authenticating customer tracking paths.
- Do not expose any client-to-server message endpoint.
- Add no CSRF exemption because SSE uses a safe `GET`.

### Step 7: Dependency cleanup

- Remove Spring WebSocket and WebSocket test starters if no remaining code uses them.
- Retain Spring MVC and Spring Data Redis.
- Disable unused Redis repository scanning in the main application configuration if necessary.
- Keep the Redis Actuator health contributor enabled.
- Keep Caddy's existing `/api/*` reverse proxy; `text/event-stream` is carried through the same-origin route.

## 7. Panel Implementation

### API module

- Remove `CREATED` from the Zod status enum.
- Add `label` to the staff order schema.
- Extend `createOrder(...)` with Auto and Custom inputs.
- Auto sends no label; Custom sends the stripped label.
- Continue using the shared authenticated `request(...)` boundary and CSRF handling.

### Order management component

- Add a minimal HeroUI Auto/Custom selector above the create action.
- Select Auto by default.
- Show the label input only in Custom mode.
- Validate custom input with Zod before mutation.
- Display validation feedback without issuing a request.
- After creation, show the returned label in the queue and QR section.
- Remove `CREATED` labels and transitions.
- For `IN_PREPARATION`, show actions for Ready and Cancel.
- For `READY`, show actions for Complete and Cancel.
- Do not add cancellation confirmation.
- Remove a successfully completed or canceled order from the local active cache, then revalidate.
- Keep QR generation client-side and display only the QR code.
- Remove the current staff-side “Open customer tracking page” link.

Do not redesign layout or visual styling beyond what is necessary for the new controls and data.

## 8. Customer Implementation

### REST and event schemas

- Correct the tracking REST path to `/api/tracked-orders/{trackingReference}`.
- Remove `CREATED` from the customer status schema.
- Add `label` to the customer order schema.
- Add a Zod schema for the SSE event contract.

### Tracking component

- Continue loading current state with SWR before creating the event stream.
- Display the customer-visible label and current status.
- Open a same-origin native `EventSource` only while the current order is active.
- Manage EventSource lifecycle through SWR's external-source subscription abstraction rather than a component effect.
- On a valid matching event, call SWR `mutate()` without applying event data to the cache.
- Keep the last authoritative order visible while invalidation clears and refetches the SWR entry, so stream eligibility remains stable during revalidation.
- Revalidate immediately when EventSource opens or reopens to close the initial REST-to-stream race.
- Mark the stream disconnected on error and enable a 15-second SWR refresh interval.
- Stop fallback polling as soon as the stream opens.
- Preserve manual refresh and normal focus/reconnect revalidation.
- Close EventSource and stop polling after REST returns `COMPLETED` or `CANCELED`.
- Keep the terminal view readable indefinitely.
- Do not add alerts, sound, vibration, Wake Lock, service workers, or other PWA behavior.

## 9. Backend Test Plan

Add or update backend tests only.

### Domain and service integration

- Creation starts in `IN_PREPARATION`.
- Creation writes one `IN_PREPARATION` history record with authenticated user identity.
- Allowed and rejected transitions match the new lifecycle.
- Completed and canceled orders disappear from both staff list variants.
- Terminal orders remain available through tracking REST.
- Staff tenant and location authorization remains unchanged.

### Label behavior

- Auto starts at `"1"` independently for each location.
- Auto increments within one UTC date.
- Auto resets to `"1"` on the next UTC date.
- A custom label is stripped and persisted.
- A custom label advances the daily ordinal used by later automatic labels.
- Duplicate custom and automatic label text is accepted.
- A canceled or completed automatic order does not release its number.
- Blank, multiline, control-character, and over-32-character custom labels are rejected.
- Boundary-length and ordinary Unicode labels are accepted.
- Concurrent automatic creation at one location produces distinct monotonically increasing numbers.
- Concurrent creation at different locations does not share a counter.

Use a fixed or controllable injected `Clock` for date-boundary tests. Keep the concurrency test outside a test-managed transaction so independent worker transactions can acquire the location lock.

### REST, security, and SSE

- Staff creation accepts Auto and Custom request shapes and returns `label`.
- Staff list responses contain active orders only.
- Anonymous tracking returns label and terminal state.
- Unknown tracking references return `404`.
- The SSE route is anonymous and read-only.
- Invalid or unrelated Redis payloads are ignored.
- A committed transition produces one serializable Redis event.
- A rolled-back transition produces no Redis event.
- Redis publication failure does not change the committed transition result.
- A Redis event reaches every locally registered emitter for its reference and no other reference.
- Terminal events complete and remove local emitters.
- Emitter completion, timeout, and send failure clean up registry state.

Prefer focused unit/component tests around the Redis adapter and SSE registry. Do not make the normal backend suite depend on the user-owned Compose Redis container.

## 10. Verification

The user approved backend automated tests for this increment and excluded frontend tests.

Run:

```bash
cd apps/api
./mvnw clean test
```

Run frontend static checks:

```bash
npm --prefix apps/panel-app run check
npm --prefix apps/customer-app run check
```

Always run:

```bash
git diff --check
```

Do not run browser smoke tests, start another Compose Watch process, or exercise the user-owned runtime unless the user separately requests runtime verification.

## 11. Implementation Order

1. Update `V1`, time configuration, entities, lifecycle, and response models.
2. Implement label validation and concurrency-safe automatic allocation.
3. Update active queue repository queries and backend order tests.
4. Add the after-commit event and Redis adapter with focused tests.
5. Add the SSE registry, endpoint, security checks, and backend tests.
6. Update the panel request contract and minimal creation/queue UI.
7. Update customer REST contract, EventSource lifecycle, and fallback polling.
8. Remove obsolete WebSocket dependencies and stale endpoint/status references.
9. Run the approved backend suite, both frontend static checks, and `git diff --check`.
10. Re-read the requirements and inspect every changed consumer before handoff.

## 12. Completion Checklist

- [ ] No production or test reference to `CREATED` remains.
- [ ] New orders persist `IN_PREPARATION` and one matching history entry.
- [ ] Labels satisfy the agreed Auto and Custom behavior.
- [ ] Automatic allocation is concurrency-safe per location and UTC date.
- [ ] Staff list endpoints return active orders only.
- [ ] Customer REST uses the canonical `/tracked-orders` path and returns label.
- [ ] Redis publication happens after commit and failures are isolated.
- [ ] SSE is anonymous, reference-scoped, customer-only, and read-only.
- [ ] Valid SSE events trigger REST revalidation rather than direct cache updates.
- [ ] Disconnected SSE enables fallback polling; terminal state stops both.
- [ ] No deferred UI, alert, PWA, POS, outbox, archive, or expiration feature was added.
- [ ] Backend automated tests pass.
- [ ] Panel and customer static checks pass.
- [ ] `git diff --check` passes.
- [ ] Documentation and implemented behavior agree.
