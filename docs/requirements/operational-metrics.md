# Operational metrics

## Contents

- [Operational metrics](#operational-metrics)
- [Contents](#contents)
- [Collection and privacy](#collection-and-privacy)
- [Workers and backlog](#workers-and-backlog)
- [Protected export and limits](#protected-export-and-limits)

## Collection and privacy

Actuator/Micrometer supplies HTTP server request counts, errors and durations,
JVM/process meters and datasource connection-pool capacity/usage meters. Database
health remains part of Actuator health. HTTP request labels use framework route
templates, bounded to 200 URI values; unmatched requests do not use raw paths.
Automatic outbound HTTP meters are disabled to avoid recipient/provider host or
URI disclosure. Custom labels are fixed worker types, delivery channels and
outcomes. No account, customer, tenant, location, order, tracking, subscription,
IP address, destination, payload, credential or free text is a metric dimension.

## Workers and backlog

`kairos.worker.cycle.duration` times analytics, webhook/push fan-out and delivery,
push cleanup and retention cycles. `kairos.worker.failures` counts unexpected
cycle/task failures, deferred retention-provider failures, push preparation
failures and failed backlog snapshots. Delivery attempts have a
channel-only duration and fixed outcomes: webhook `accepted`, `failed`, `stale`;
push additionally `transient_failure` and `invalid_subscription`. These measure
transport results after completion returns, not unique notifications, business
transitions or final push states. Deadline expiry and retry scheduling can differ
from the HTTP outcome. A stale claim is reported separately.

A fixed, aggregate-only database function supplies pending counts and oldest ages
for analytics inputs, webhook/push fan-out and webhook/push deliveries. Pending
includes unconsumed inputs, unfinished fan-out and all `PENDING`/`PROCESSING`
deliveries, including future retries and active claims. Terminal deliveries are
excluded. Ages use input occurrence/outbox occurrence/delivery creation times,
clamped to zero for a backwards clock. No row identities or tenant breakdowns
leave this function; existing RLS policies remain unchanged.

A read-only snapshot refreshes every 30 seconds with a two-second SQL statement
limit. Scrapes use cached gauges without database queries. Before the first
successful refresh backlog gauges are NaN; a failed refresh preserves the last
snapshot and sets its `up` gauge to zero. Its last-success epoch timestamp lets a
collector detect staleness. These measurements do not grant reporting access.

## Protected export and limits

Only GET `/api/actuator/prometheus` accepts a dedicated operator bearer credential
from an externally managed file. Authentication is stateless and independent of
staff sessions and external integration keys. Missing or invalid secret files
disable export without disabling the API. Public health behavior is unchanged;
NGINX forwards only that exact management resource with `Cache-Control: no-store`;
other management endpoints remain unavailable through ingress. See the
[operator procedure](../deployment/OPERATIONAL_METRICS.md).

Counters and timers reset on process restart. Attempts can be lost around a crash
or repeated during recovery and are not authoritative business counts. Backlog
counts are cross-tenant operational totals, approximate snapshots, and lose
purged/deleted records. No historical samples are retained until an external
collector is connected. Collector/Grafana deployment, alerts, tracing, reporting
APIs and dashboards are outside this increment; Redis remains unchanged.

If oldest analytics input age persistently exceeds about one minute, compare cycle
duration and datasource pressure before tuning batching/indexes or worker count.
Monitor snapshot freshness and scrape failures as well as the backlog itself.
Growing snapshot query cost or timeouts calls for query/index tuning before
increasing its frequency. Pool pressure is a contention signal, not a direct
measurement of PostgreSQL lock waits. Host/disk monitoring remains separate.
