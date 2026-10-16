# Anonymous panel and review usage counts

## Contents

- [Anonymous panel and review usage counts](#anonymous-panel-and-review-usage-counts)
- [Contents](#contents)
- [Events and attribution](#events-and-attribution)
- [Ingestion and delivery](#ingestion-and-delivery)
- [Storage, isolation and deletion](#storage-isolation-and-deletion)
- [Accuracy and privacy limits](#accuracy-and-privacy-limits)

## Events and attribution

Count actions, never unique people, sessions or devices. The panel collects
`queue_opened`, `menu_editor_opened`, `integration_settings_opened` and
`order_creation_form_opened`. Queue opening includes the initial visible queue
and actual entry into the Orders workspace. Menu means entry into the Menu
workspace, including through a location's Edit menu action, rather than each
save. Creation counts each
actual form opening, not successful order creation. Renders, refreshes and clicks
on the already selected workspace do not count. Successful business transitions
remain authoritative [order analytics](order-analytics.md).

Queue and creation-form actions use the selected authorized location when
present; tenant-wide queue, menus and integrations use tenant scope. The API
authenticates staff, derives tenant ownership and validates action/location
access. Clients cannot supply tenant ownership.

Customer actions are `review_link_clicked` and `review_prompt_dismissed`.
Dismissal means explicit Close, X, backdrop or Escape; a Google click consumes
the local invitation without also counting as dismissal. Navigation, unmount,
offline state and absent telemetry never imply dismissal or an ignored prompt.
The existing high-entropy tracking reference authorizes only an eligible live
completed order's review action after its server-side due time. The API derives
tenant and required location from that order and current review configuration.
The reference is transient authorization, never an aggregate dimension.

## Ingestion and delivery

`POST /api/usage-analytics/v1/actions` accepts an `actions` array of one to
20 entries, each with a predefined `event` and optional `locationId`. Each entry
counts once; arbitrary counts, timestamps and labels are not accepted.
`POST /api/tracked-orders/v1/{trackingReference}/review-actions` accepts one
predefined review `event`. Accepted requests return `204`. Both use server
receipt time and reject invalid requests atomically. Unsafe browser requests
retain CSRF protection and origin-scoped CORS.

Bodies are limited to 8 KiB, including bodies without Content-Length. A budget
of 60 requests per clock minute per staff account or tracking reference is
counted in Redis, so every API instance shares it. Counters expire after two
minutes; no caller identity or IP is persisted in analytics. A review action
uses the budget only once its order is found and its invitation is due, so
made-up references cannot exhaust it. Redis being unavailable returns `503`.

Frontends initially submit singleton requests asynchronously with silent failure.
Collection never delays or blocks the user action. There is no browser telemetry
queue, offline persistence or automatic retry of uncertain submissions. A retry
following a confirmed CSRF rejection does not repeat an accepted request.
Collection begins with deployment, without historical backfill.

## Storage, isolation and deletion

A separate PostgreSQL table holds additive action counts by tenant, optional
location UUID, UTC receipt hour and predefined event. Accepted batches increment
counts atomically. No durable raw browser-input queue is introduced. Ordinary
staff, customer and integration contexts cannot read aggregate data; narrowly
scoped ingestion permits only authorized increments.

Tenant-first locking serializes ingestion with permanent tenant deletion; tenant
foreign-key cascade removes all action aggregates without concurrent recreation.
Location keys have no foreign key to operational locations, so location purge
preserves counts. Aggregates are retained indefinitely for now. Reports follow
[analytics reporting](analytics-reporting.md).

## Accuracy and privacy limits

Counts are best-effort: navigation, offline use, throttling and network failures
can lose activity; repeated submissions can duplicate it and authorized clients
can fabricate actions. A Google link click does not prove a review was submitted.
No customer, account, session, device, IP, order or tracking identifiers, URLs,
labels, free text, review text or raw request bodies are stored in aggregates.
Tenant/location attribution and sparse counts may reveal business activity
through outside knowledge. Operational access logs remain separate.

Legal, consent and retention assessment remains in [legal readiness](legal.md)
and issue #71. It may revise collection policy; this implementation does not
claim that anonymous aggregate storage settles those questions.
