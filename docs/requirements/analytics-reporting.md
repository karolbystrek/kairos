# Analytics reporting and platform health

## Contents

- [Analytics reporting and platform health](#analytics-reporting-and-platform-health)
- [Contents](#contents)
- [Audiences and scope](#audiences-and-scope)
- [Platform authority](#platform-authority)
- [Report contents and semantics](#report-contents-and-semantics)
- [Dates, zones and ranges](#dates-zones-and-ranges)
- [Retention, purge and deletion](#retention-purge-and-deletion)
- [Current platform health](#current-platform-health)
- [Panel presentation](#panel-presentation)
- [Boundaries](#boundaries)

## Audiences and scope

Reports read the hourly aggregates of [order analytics](order-analytics.md) and
[usage analytics](usage-analytics.md) through `GET /api/analytics/v1/report`.

- Tenant administrators report their tenant, or one location of it selected by
  `locationId`. An unknown or foreign location returns 404 without disclosure.
- Managers report only their current enabled assigned location; another
  `locationId` is denied.
- Operators, customers and integration credentials have no analytics access.

The API derives tenant ownership from the session; clients cannot widen it.
The database repeats the scope: order aggregates are read through the
`order_hourly_reports` security-barrier view and usage aggregates through
row-level security, both admitting only the tenant administrator, the current
manager of that location, or the bound platform reporting scope. The analytics
worker's own upsert policy is left unchanged so reporting adds no per-row cost
to aggregation. Tenant-only usage actions (no location) appear in
tenant reports and never in location or manager reports.

## Platform authority

Platform reporting is a separate authority held in `platform_administrators`.
The runtime database role can read it only through a security-definer check and
can never write it, so registration, invitations and tenant administration
cannot grant it. The database owner grants and revokes it manually:

```sql
INSERT INTO platform_administrators (account_id)
SELECT id FROM accounts WHERE email = 'owner@example.com';
DELETE FROM platform_administrators
WHERE account_id = (SELECT id FROM accounts WHERE email = 'owner@example.com');
```

The holder must remain an enabled staff account. Every platform request
rechecks the grant, so revocation, disabling or account deletion takes effect
on the next request under the existing session rules. `GET /api/auth/v1/me`
adds `VIEW_PLATFORM_ANALYTICS` for holders and `VIEW_ANALYTICS` for tenant
administrators and managers.

Platform requests bind a dedicated `platform-analytics` database scope that
reads only the two hourly aggregate tables. It grants no order browsing, tenant
impersonation, tenant management or tenant rankings. Its endpoints are
`GET /api/platform-analytics/v1/report` and
`GET /api/platform-analytics/v1/health`.

## Report contents and semantics

Each report returns, for the range:

- Order transition totals and per-day series for `IN_PREPARATION` (created),
  `READY`, `COMPLETED` and `CANCELED`.
- Mean preparation (creation to ready) and collection (ready to completion)
  durations with sample counts. Means divide merged duration totals by merged
  sample counts, never averages of hourly means. Zero samples yield no mean.
- Counts of the four predefined panel actions, review-link clicks and explicit
  review-prompt dismissals.
- For tenant-wide administrator reports only, per-location transition totals.

Usage counts are approximate browser-reported actions, never unique people.
Clicks do not establish submitted reviews, and same-period created/completed
counts are not a cohort completion rate. Percentiles are deferred.

Metadata includes `generatedAt`, the reporting zone, `partialDay` when the range
ends today, and `firstRecordedHour`, the earliest aggregate hour in scope.
Collection started at deployment without backfill, so days before that hour are
shown as no data rather than zero. Reports do not claim an exact
processed-through watermark; recent transitions may still await the analytics
worker.

## Dates, zones and ranges

Ranges are browser-local calendar dates with an explicit IANA zone (`zone`);
fixed offsets and unknown names are rejected. Day boundaries are computed in
that zone and are daylight-saving aware. Storage remains `timestamptz` with UTC
hourly aggregates: each whole aggregate hour belongs to the local date of its
start instant. Where local midnight falls inside a UTC hour (fractional
offsets such as `Asia/Kolkata`), day boundaries are therefore approximate to
within that hour.

The panel defaults to the last 7 calendar days including today and offers 30
days and custom ranges. The API accepts at most 90 calendar days, ordered,
ending no later than today in the reporting zone, which bounds every response.

## Retention, purge and deletion

Location purge preserves aggregates. Purged or archived locations keep their
own UUID in tenant breakdowns and are labelled Deleted location; no historical
location name is stored. Permanent tenant deletion cascades its aggregates and
platform grants, so its contribution disappears from platform totals and is
never recreated or copied.

## Current platform health

The platform health endpoint returns a sanitized current snapshot: observation
time, PostgreSQL status, Redis status (`UP`, `DOWN`, or `UNKNOWN` when the ping
does not answer within two seconds), and the cached
[worker backlog](operational-metrics.md) with pending counts and oldest ages,
its observation time and whether the latest refresh succeeded. A missing
snapshot is reported as unknown, never zero. PostgreSQL is reported up because
the request was authorized through it; when it is down, the request fails.

It never exposes Actuator or export endpoints, credentials, hosts, history,
uptime or tenant/location labels. Backlog age describes current processing
delay, not reporting completeness. Restaurant users receive ordinary 403
feedback.

## Panel presentation

The panel adds an Analytics tab for `VIEW_ANALYTICS` and a Platform tab for
`VIEW_PLATFORM_ANALYTICS`. Analytics cache keys include account, scope,
location, range and zone, so another scope never shows a previous scope's
results. A failed refresh keeps the last result with a stale notice; a 403
clears platform data and refreshes capabilities. Health refreshes every 30
seconds; a failed browser request is shown as unknown or unreachable because it
cannot distinguish an API outage from a network failure. Daily trends render as
a table with decorative bars, which is also the accessible equivalent.

## Boundaries

No customer dashboard, operational sample history, alerts, collector
deployment, live infrastructure changes, CSV exports, comparisons, new tracked
dimensions or finer-grained aggregation are included.
