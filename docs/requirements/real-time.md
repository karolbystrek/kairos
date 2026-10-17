# Real-time communication

## Real-time communication

The API exposes an anonymous, read-only Server-Sent Events stream for each
customer tracking reference. Possession of the high-entropy reference grants
access only to that order's customer representation, never to staff data or
commands. The stream sends an `order-status-changed` event containing
`eventId`, `trackingReference`, `number`, nullable `label`, `status`, `updatedAt`, and
`reviewInvitation` on opening and reopening, and after status changes. The
client validates the state with Zod and applies it directly without an extra
REST request. An already terminal order receives its final snapshot and the
stream ends; terminal changes likewise deliver state before closing.

PostgreSQL remains authoritative. Creation and status events are published
only after the order, history and outbox transaction commits. One Redis Pub/Sub
topic distributes committed order changes to every API instance for both
customer and staff streams, without sticky routing. Redis publishing and
subscription are mandatory application behavior with no feature flag; Redis
health reporting remains enabled. A publication failure cannot turn a committed
command into a failed response.

Redis Pub/Sub and SSE are non-durable. When an instance subscribes again after
Redis recovery, it refreshes its connected customer and staff streams from
PostgreSQL. Customer REST remains available for initial loading, focus and
browser connectivity recovery, with approximately 15-second polling while the
stream is unavailable. Healthy streams require no periodic REST safety request.
Every open customer and staff stream also re-sends its current state read from
PostgreSQL on one shared interval (`kairos.live-updates.resync-interval`, 60
seconds by default), so a lost publication without subscription interruption
leaves a page stale for about a minute at most. Customer REST, stream snapshots,
stream events, Web Push messages and IndexedDB persistence share monotonic
status reconciliation: `IN_PREPARATION` advances to `READY` or a terminal state,
`READY` advances only to a terminal state, and a terminal state never changes.
Status precedence, rather than wall-clock timestamp order, prevents regression.

Authenticated staff use `/api/orders/v1/events` with an optional `locationId`,
matching the queue REST endpoint's location and tenant-wide access rules. The
`order-queue` event carries the full current active queue on opening/reopening
and after relevant committed creations or transitions. Connections watching the
same location share one queue query per change; a tenant-wide stream shares only
with the same account's devices. Coalesced refreshes deliver the newest queue
last, and at most four queue queries run at once per instance, so a Redis
resubscribe cannot exhaust the database pool. The session cutoff and location
access are checked on opening and rechecked on every periodic re-sync; a revoked
session or removed assignment ends the stream within one interval. Staff state stays scoped to the signed-in
account and queue, with REST recovery while disconnected. Servlet async and
error redispatches preserve the original request's authorization decision.

Push and webhook workers wake asynchronously after an order transaction
commits, run fanout before delivery, and coalesce overlapping wakeups while
draining available batches. PostgreSQL claim locks and delivery leases retain
the existing multi-instance at-least-once guarantees. Five-second recovery
cycles process abandoned work and due retries without frequent idle polling.

Web Push complements rather than replaces this foreground contract. `READY`,
`COMPLETED`, and `CANCELED` order events are durably fanned out to enrolled
browser subscriptions. The service worker treats a push as a last-known
transition snapshot and notification trigger; opening or focusing the
application still reconciles authoritative state through REST. At-least-once
delivery, unordered push services, and multiple subscriptions require stable
event IDs, state-graph monotonicity, replacement tags, and pre-submission checks
against current PostgreSQL order state. Google review invitations are in-app
only and never produce a push delivery. Future genuinely bidirectional features may introduce
WebSocket independently rather than changing the SSE or Web Push contracts.
