# Current delivery status and roadmap

## Current delivery status and roadmap

The current walking vertical slice is implemented for local development:

* persisted labeled-order creation and controlled transitions;
* public restaurant-owner landing page at the panel application's `/`,
  with plain-language copy, an in-browser order demo, direct
  registration/sign-in actions and secondary creator contact,
  plus session-aware navigation to `/dashboard` and `/login`;
* authenticated, tenant- and location-authorized staff operations;
* API-owned email/password authentication with PostgreSQL-stored hashes, email-verified
  public tenant registration, emailed recipient-bound manager/operator invitations with replacement and
  full history,
  durable rolling browser sessions, password changes and logout;
* on-screen customer QR codes and anonymous tracking through REST;
* state-carrying customer SSE and authenticated live staff queue snapshots on
  one Redis Pub/Sub topic, subscription-recovery snapshots, monotonic customer
  reconciliation and asynchronous commit-triggered push/webhook workers with
  five-second recovery polling;
* customer PWA manifest, order-aware first-launch, unclosed-order restoration,
  scan-first default page, explicit terminal-order closing, active-only
  IndexedDB snapshots, in-app QR scanning, generated Serwist service worker,
  explicit offline fallback,
  app-level notification consent and controls, application badges, and monotonic
  privacy-preserving push handling, with the final regular, maskable,
  Apple-touch, and favicon assets supplied and device acceptance still
  outstanding;
* administrator-managed External Integrations, API Keys, and webhook
  subscriptions;
* administrator-managed Locations with normalized names, enabled, disabled,
  and archived lifecycle state, atomic Account and invitation cascades,
  integration-grant cleanup, zero-enabled-location Orders recovery, and
  disabled-interval webhook fan-out boundaries;
* per-location Menus managed by administrators or the location's manager, and daily order numbers with optional labels and menu items;
* enabled, disabled, and archived member Accounts with statusless retained
  Location Assignments, nondisclosing authentication eligibility, and
  irreversible Account Delete;
* versioned external order commands with idempotent creation and desired-state
  updates;
* durable anonymous hourly order counts and versioned preparation/collection duration summaries, with transaction-safe collection and scoped background processing;
* approximate anonymous panel opening and customer review click/dismissal counts, with bounded authorized ingestion and hourly aggregation;
* one channel-neutral transactional order outbox with independent, durable
  retrying webhook and Web Push delivery under the [delivery policy](resilience.md);
* asynchronous commit-triggered email delivery with recovery polling, shared
  staff queue reads per refresh cycle and bounded customer stream recovery;
* one API configuration with externally supplied private keys;
* repository-owned local setup for configuration, complete key validation,
  restrictive permissions, and explicit existing-key handling;
* production hostname-routing NGINX HTTPS ingress, unbuffered SSE, internal health
  paths, health-gated dependencies, persistent PostgreSQL,
  and nondurable Redis;
* restricted PostgreSQL runtime identity, transaction-local tenant/location and
  capability scopes, command-specific RLS, narrow credential-bootstrap
  projections, Hibernate onboarding workflows, per-item workers and startup verification;
* fresh PostgreSQL entrypoint role initialization and automatic Spring Flyway V1;
* GitHub Actions validation for pull requests and `main`.

### Implemented behavior

The panel removes terminal orders from the active queue after an accepted
transition and shows the customer QR code without a separate tracking-link,
printing, or download workflow.

The customer PWA now supplies the referenced 192×192 and 512×512 regular
icons, 512×512 maskable icon, 180×180 Apple touch icon, and multi-resolution
favicon. Completing installability acceptance requires checking installation
and standalone launch behavior on Android Chrome and iOS Safari. The manifest
identity and one-time order-aware installation bootstrap must not be redesigned
while completing that acceptance gap.

### Next integration and authentication acceptance

The next External Integration increment publishes an OpenAPI document, rendered
public reference documentation, and formal automated public-contract checks.
The handwritten frontend clients remain in place; generated SDKs are a later,
separate decision.

Automated checks cover the API-owned credential flow; live acceptance against the hosted gateway remains outstanding. Direct
member creation through `POST /api/accounts/v1` remains
unavailable; managers/operators join only through fixed Account Invitations.

The implemented administrative-lifecycle increment provides
tenant-administrator Location management, validates and completes account
registration after email verification before requiring first-location creation in the dashboard,
protects the last non-archived location from deletion, retains the reusable zero-enabled-location creation flow in
Orders, standardizes the managed-resource lifecycle vocabulary, adds Account
archival through Delete, and applies the Location cascades and contracts
specified in [location lifecycle](locations.md) and [HTTP contracts](http-contracts.md), across the schema, API, panel, and automated verification.

Optional per-location Google review invitations now include administrator link
configuration, configurable completion delay and a closable in-app prompt; no
review push or reminder schedule is sent. Best-effort review clicks and explicit
dismissals follow [usage analytics](usage-analytics.md). Physical-device
acceptance remains outstanding.

Administrators and managers see role-scoped business, panel-usage and review
analytics in the panel; a manually granted platform administrator also sees
cross-tenant totals and current API health. See
[analytics reporting](analytics-reporting.md).

Draft Polish terms, privacy notices and their in-app entry points are in place;
the remaining [legal readiness gates](legal.md#launch-blockers) must close
before real users.

Operational instrumentation and a dedicated, secret-file-authenticated Prometheus
export are implemented; see [operational metrics](operational-metrics.md). No
collector is connected or operational history retained by this increment.

### Outstanding security and acceptance

* verify restricted-role RLS and scoped application/worker behavior on the hosted
  runtime; repository implementation and disposable PostgreSQL tests do not
  establish live acceptance;
* verify hosted gateway client addressing, throttling and origin-bypass blocking
  through Cloudflare; extend throttling when future recovery or linking routes
  are introduced;
* provide externally managed webhook-secret encryption keys,
  VAPID signing keys, push-subscription and transactional-email encryption keys, with documented
  rotation procedures, security-event retention, monitoring, and dependency
  patching;
* complete Android Chrome, iOS/iPadOS Safari, desktop Chrome, and desktop Firefox
  service-worker, offline, subscription, notification, click, badge, and
  subscription-replacement acceptance.

The current public single-VM deployment is a disposable development environment.
Native Lightsail email alarms were activated on 2026-10-08 with a verified
regional contact, enabled notifications and successful AWS notification tests;
the owner confirmed email receipt. See [monitoring](../deployment/MONITORING.md)
for coverage and [activation evidence](https://github.com/karolbystrek/kairos/issues/18#issuecomment-6068946175).
GitHub failed-workflow email delivery remains unverified.

### Deferred work

Nightly on-host database dumps run with seven-day retention. Launch gate: before
real users, enable Lightsail automatic snapshots and a disk-usage alarm (AWS
changes needing explicit approval). Retention/recovery targets and restore
rehearsals are deferred until real users or valuable data under the
[operations policy](routing-releases.md#development-operations-policy). Disk-space,
memory-pressure and public HTTPS availability alerts are also deferred in favor
of native Lightsail status, CPU and CPU burst-capacity alarms. No custom host
scripts, monitoring agents or external monitoring service are introduced.

Deferred operational and product work includes order archives and search, printable QR artifacts,
tracking-reference expiration, session-management UI, additional
administrators, CAPTCHA, MFA,
passkeys, webhook DLQ
inspection and alerts, manual webhook redelivery, strict delivery
ordering, application-owned install prompts, and native mobile variants. Any of
these requires an explicitly approved increment and synchronized changes to
the [canonical requirements](../REQUIREMENTS.md).
