# Resilience and consistency

## Contents

- [Resilience and consistency](#resilience-and-consistency)
- [Contents](#contents)
- [8. Resilience and Consistency](#8-resilience-and-consistency)

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
* Optional review push delivery starts at the captured completion follow-up due
  time, with the same ten-minute freshness window and bounded retries measured
  from that due time. It has its own identity/topic and cannot replace completion
  notifications. The location's configuration identity and enabled state are
  revalidated before submission; there are no scheduled reminder deliveries.
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
