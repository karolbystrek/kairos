# Anonymous order analytics

## Collection and activation

The API collects durable restaurant business measurements when an order is
actually created, becomes ready, completes, or is canceled. Exact creation
replays and repeated same-state commands add no measurements. Each input commits
in the order transaction together with history and the delivery outbox; rollback
leaves no measurement. History and webhook/Web Push outbox remain intact.

Collection starts when the new collector code is deployed. There is no historical
scan, configurable activation date, or acceptance-time cutoff. Existing orders
contribute subsequent accepted transitions and durations available from their
lifecycle timestamps; their past creation does not receive an invented count.
Reports follow [analytics reporting](analytics-reporting.md); cloud deployment
is a separate action.

Preparation duration runs from creation to readiness. Collection duration runs
from readiness to completion. Both use accepted transition times, independent of
worker processing time. A backwards clock still contributes the transition count
but supplies no duration rather than inventing a nonnegative elapsed time.

## Hourly summaries

Aggregates are keyed by tenant, original location UUID, UTC hour, resulting order
state and histogram version. Creation is represented by `IN_PREPARATION`.
Transition counts belong to their acceptance hour; preparation and collection
durations belong to the finishing transition hour. Completed/created in the same
hour is throughput, never a cohort completion rate.

Each summary holds a transition count and duration count, total milliseconds,
and eight fixed bucket counts. Version 1 uses these upper-exclusive boundaries:
60,000; 120,000; 300,000; 600,000; 900,000; 1,800,000; and 3,600,000 milliseconds.
The final bucket includes durations of at least one hour. Zero duration is valid
and belongs to the first bucket. Duration counts and bucket counts merge by
addition, as do totals; versions must not be merged together. Cancellation and
creation counts have no duration samples.

Inputs and aggregates contain no order, tracking, customer, staff, integration
identity, label, or payload content. Inputs have only their own queue identity,
tenant/location ownership, state, acceptance time and optional duration. Consumed
inputs are deleted; aggregates are retained indefinitely for the tenant.

## Processing and ownership

A scheduled API worker polls every ten seconds, processing up to 1,000 inputs per
cycle in separate short transactions. Normal-operation freshness targets roughly
one minute; outages leave a durable backlog for subsequent cycles. PostgreSQL
serializes consumption with row locks and atomically merges the aggregate and
deletes the input. Failed transactions leave both unchanged, so restart/retry and
concurrent workers cannot lose or duplicate committed activity.

Worker discovery locks the tenant before the selected input. Its transaction-local
capability permits only that input and its corresponding hourly aggregate; it
confers no order, payload, or other-tenant access. Ordinary staff, integration,
and anonymous contexts cannot read aggregates in this increment. Input insertion
requires the existing authorized order-write location and matching tenant.

Location purge retains both pending inputs and aggregates. Their captured location
UUID has no foreign key to the operational location, and processing requires no
order or location reads. This removes the purge/processing dependency even during
an outage. Permanent tenant deletion erases both tables through tenant foreign-key
cascades. Tenant locks serialize deletion with processing and prevent concurrent
aggregate recreation. Permanent tenant deletion remains a manual operator action;
no new deletion endpoint is introduced. See [isolation](access-isolation.md),
[persistence](persistence.md), and [retention](resilience.md#retention-purge).
