# Synthetic capacity benchmark

Use this procedure before increasing capacity or introducing a persistent
response cache. It defines experiments, not a promise about the current VPS.
Run only in an explicitly selected disposable environment, with synthetic
accounts/orders and the intended VPS's CPU, memory and disk limits. Never direct
stress traffic at the public site by copying its production URLs or credentials.

## Record the environment

Record the source revision, image digests, CPU/RAM limits, PostgreSQL/Redis
versions, pool/scheduler sizes, recovery intervals and dataset size with each
result. Use production builds and the production gateway path; development hot
reload does not represent deployed capacity. Use the normal single-instance
topology. Provisioning a separate host or changing cloud resources needs its own
approval; do not disturb the developer's running Compose services or volumes.

Use a load generator that supports long-lived SSE and an open arrival rate
(new work continues at the chosen rate even when responses slow). Keep its
process and network capacity separate from the measured host. Capture the
generator configuration with the result so the same scenario can be repeated.
No load-generator service or application dependency is required by Kairos.

Transactional email must use `LOG` and outbound push/webhook endpoints must be
controlled test receivers. Do not send load to real recipients or public
providers. Any relaxed destination policy belongs exclusively to the isolated
test environment, never the hosted production configuration.

## Workload levels

| Level | New orders/minute | Concurrent customer streams | Staff streams |
| --- | ---: | ---: | ---: |
| A: pilot experiment | 10 | 100 | 1 |
| B: growth experiment | 100 | 1,000 | 10 |
| C: stress experiment | 1,000 | 10,000 | 50 |

These are experimental levels, not demand forecasts. Customer counts represent
approximately ten minutes of tracking at each arrival rate. Warm each level for
five minutes and measure for fifteen; allow a separate recovery period afterward.
For an independent resync experiment, pre-create exactly 100, 1,000 or 10,000
active orders and connect one stream per distinct reference before measuring.

Create orders through the staff or external API, retaining normal authentication,
tenant checks and transactions. Use unique external idempotency keys, a fixed
menu-item distribution and a fixed random seed. Record all request shapes and
the chosen distribution. Move 95% to `READY` at five minutes and `COMPLETED` at
ten minutes; cancel the remaining 5% at five minutes. Customer clients make the
initial tracked-order REST read, open SSE, apply snapshots and stop on terminal
state. Healthy clients do not issue periodic REST reads. Staff streams subscribe
to their assigned queues; include a tenant administrator's all-location queue.

Repeat the same level with orders distributed across locations/tenants, then
with all staff terminals watching one busy location. Preserve the same order
arrival rate and order mix between runs so scope sharing is the changed variable.
Do not use a single tracking reference for the customer population: the API's
five-stream/reference limit is intentional, and sharing one order hides real
database recovery cost. Include gateway limits in the result; expected `429`
responses must be reported separately from server errors. For example, a
10,000-stream run cannot use one source IP through the 1,000/client gateway cap.

## Failure and recovery cases

Run each case after a steady baseline, keeping the same dataset and traffic:

1. Reconnect 25% of customer streams over ten seconds. Count initial REST reads,
   stream snapshots and database recovery queries. Repeat with 1, 10 and 50 staff
   screens watching the same queue; record queue reads per accepted mutation.
2. Interrupt only the test Redis connection, then restore it. Verify current
   state returns through resubscription/periodic recovery and no customer status
   regresses. Record recovery time, database CPU and pool waits during the burst.
3. Make controlled webhook/push receivers take ten seconds or return transient
   failures. Exercise email with a delayed test sender rather than `LOG` for
   this case. Measure worker backlog age and heartbeat gaps while retention and
   analytics run. Never substitute real providers for the test receivers.
4. Stop arrivals and allow outstanding work to drain. Record time to recovery,
   retained heap/RSS, connection-pool wait and oldest due delivery age. A system
   that survives the run but cannot clear its backlog has exceeded capacity.

For database investigation, capture `EXPLAIN (ANALYZE, BUFFERS)` for the exact
recovery query under the restricted runtime role and matching transaction-local
tracking authority. Owner/superuser query plans bypass RLS and are not comparable.
Compare groups of at most 500 with the previous unbounded approach only in the
disposable environment. Record total time across all groups, not just the
fastest group. Preserve normal item sizes; customer projections do not load
order items, while staff queues fetch their item snapshots in batches.

## Measurements and provisional budgets

Use the protected [operational metrics export](OPERATIONAL_METRICS.md), bounded
database observations and generator timestamps. Never put tenant/account/order
IDs, tracking capabilities or recipient data into metric labels.

| Measurement | Initial target |
| --- | --- |
| Steady API latency | p95 below 300 ms; p99 below 1 second |
| Healthy status-change to SSE delivery | p95 below 1 second |
| Heartbeat gaps during mixed work | Below 30 seconds |
| Unexpected `5xx` responses | Zero |
| Order numbers, tenant isolation, monotonic customer state | No duplicates, unauthorized results or state regressions |
| Sustainable worker load | No continuously growing backlog; drains after arrivals stop |
| Post-stress recovery | CPU, memory and pool waits return to a stable baseline |

The latency targets are provisional engineering budgets, not existing public
contracts. Report the complete p50/p95/p99 distribution and failed/time-out
requests, not averages alone. Measure customer resync duration, SQL count, pool
pending/active connections, database CPU and host RSS. For retries, distinguish
oldest pending age from overdue work: a future scheduled retry is not late.

Record the first level that misses a budget, its bottleneck and recovery
behavior in the relevant issue/PR. Change one limiting factor, rerun its scenario
and compare the same metrics. Retain headroom when choosing an operating limit;
an idle dashboard, passing unit tests or reduced query count cannot establish
production capacity. Live monitoring, backup/restore and device acceptance
remain separate launch checks.
