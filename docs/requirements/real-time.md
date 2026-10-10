# Real-time communication

## Contents

- [Real-time communication](#real-time-communication)
- [Contents](#contents)
- [6. Real-Time Communication](#6-real-time-communication)

## 6. Real-Time Communication

The API exposes a Server-Sent Events stream for each active customer tracking
reference. In the local environment the customer application connects to that
stream on the dedicated API HTTPS origin through NGINX. The stream is
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
against current PostgreSQL order state. Review-enabled completions also materialize one delayed review delivery before
terminal enrollments are removed. This distinct notification kind does not enter
the order state graph or affect the active-order badge; clicks recover an in-app
prompt through REST. Authenticated staff queue streaming remains deferred. Future genuinely bidirectional features may introduce
WebSocket independently rather than changing the SSE or Web Push contracts.
