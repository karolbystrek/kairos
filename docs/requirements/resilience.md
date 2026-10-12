# Resilience and consistency

## Contents

- [Resilience and consistency](#resilience-and-consistency)
- [Contents](#contents)
- [8. Resilience and Consistency](#8-resilience-and-consistency)
  - [Retention purge](#retention-purge)

## 8. Resilience and Consistency

* Order transitions and their history are committed atomically.
* Concurrent commands for the same order are serialized before validating and persisting a transition, preventing stale state decisions without duplicating an application-managed version value.
* Order transitions, required outbox events and anonymous measurement inputs are committed atomically. Analytics merge/consumption is retry-safe and independent of retained orders; see [anonymous order analytics](order-analytics.md).
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
* Terminal orders remain readable to the holder of the tracking reference until the [retention purge](#retention-purge) of an archived location, but cannot re-enter an active lifecycle.

### Retention purge

* An hourly worker erases deleted data after a grace period of 30 days by
  default (`kairos.retention.grace-period`). An archived account is removed with
  its ZITADEL identity, assignment and the invitations it issued or redeemed.
  The identity is deleted first; a missing identity counts as deleted, and a
  provider failure defers the account to a later cycle.
* An archived location is removed once no account is assigned to it, together
  with its orders, order history, outbox events, webhook and push deliveries,
  push enrollments, invitations and remaining integration selections.
* Anonymous order and usage aggregates and self-contained business inputs survive location purge; permanent tenant deletion erases them. Usage ingestion and business processing coordinate through tenant locks.
* Finished invitations are deleted 30 days after revocation or expiry.
* Each item is discovered with `FOR UPDATE SKIP LOCKED` and erased in its own
  transaction by a guarded `SECURITY DEFINER` function, so row policies stay
  closed to deletion.
* Deleting a whole tenant or an administrator account remains a manual request.
* Security events: staff sessions expire after 30 idle days and Spring Session
  removes expired rows. Production container logs, which hold IP addresses and
  security events, rotate at three 10 MB files per service
  (`compose.deployment.yaml`). ZITADEL keeps its own event store, including
  events of deleted users, under its own retention behaviour.
