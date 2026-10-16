# Resilience and consistency

## Resilience and consistency

* Transactional email acceptance commits a durable outbox row. The asynchronous
  worker retries transient failures with backoff for at most 23 hours, within
  the provider idempotency window, then records terminal failure. Reprocessing
  uses the same provider idempotency key and never sends a second message for
  one delivery. Sent/failed rows retain safe outcome metadata and erase secrets.
* Order transitions and their history are committed atomically.
* Concurrent commands for the same order are serialized before validating and persisting a transition, preventing stale state decisions without duplicating an application-managed version value.
* Order transitions, required outbox events and anonymous measurement inputs are committed atomically. Analytics merge/consumption is retry-safe and independent of retained orders; see [anonymous order analytics](order-analytics.md).
* SSE or Redis event loss does not prevent later REST recovery.
* Redis unavailability must not corrupt PostgreSQL state; event delivery may be delayed and recovered according to operational policy.
* A webhook delivery is stored as success or a terminal dead-letter result. Timeouts, network errors, `429` and `5xx` responses are retried with backoff of 30 seconds, 2 minutes, 10 minutes and 30 minutes, for at most five attempts in total, then dead-lettered; other `4xx` responses, blocked destinations and signing failures dead-letter at once. v1 has no `Retry-After` handling.
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
  default (`kairos.retention.grace-period`). Each kind is purged in batches of
  `kairos.retention.batch-size` until a batch comes back short or the cycle
  budget (`kairos.retention.cycle-budget`, 60 seconds by default) is spent, so a
  backlog larger than one batch clears within the cycle and the rest continues
  the next hour. An archived account is removed with
  its staff identity, assignment and the invitations it issued or redeemed, in
  one transaction.
* An archived location is removed once no account is assigned to it, together
  with its orders, order history, outbox events, webhook and push deliveries,
  push enrollments, invitations and remaining integration selections.
* Anonymous order and usage aggregates and self-contained business inputs survive location purge; permanent tenant deletion erases them. Usage ingestion and business processing coordinate through tenant locks.
* Finished invitations are deleted 30 days after revocation, replacement or expiry.
* A registration whose last code was sent more than 30 days ago is removed with
  its unverified staff identity, unless an account already uses that identity.
* Sent and failed email deliveries are deleted 30 days after completion once no
  invitation or pending registration references them.
* Succeeded and dead-lettered webhook deliveries are deleted 30 days after
  completion. Order outbox events are deleted once both fan-outs finished more
  than 30 days ago and no webhook or push delivery references them. Orders and
  their history remain.
* The same worker expires orders left active and unchanged for 24 hours (see
  [order lifecycle](orders-customer.md)).
* Invitation creation/replacement and its email outbox acceptance commit
  atomically. Replacement cancels queued old delivery, invalidates the old token
  and reserves the tenant/email for only one active invitation.
* Each item is discovered with `FOR UPDATE SKIP LOCKED` and erased in its own
  transaction by a guarded `SECURITY DEFINER` function, so row policies stay
  closed to deletion.
* Deleting a whole tenant or an administrator account remains a manual request.
* Security events: staff sessions expire after 30 idle days and Spring Session
  removes expired rows. Production container logs, which hold IP addresses and
  security events, rotate at three 10 MB files per service
  (`compose.production.yaml`).
