# Current delivery status and roadmap

## Contents

- [Current delivery status and roadmap](#current-delivery-status-and-roadmap)
- [Contents](#contents)
- [10. Current Delivery Status and Roadmap](#10-current-delivery-status-and-roadmap)
- [Implemented behavior and development schema](#implemented-behavior-and-development-schema)
- [Next integration and authentication acceptance](#next-integration-and-authentication-acceptance)
- [Outstanding security and acceptance](#outstanding-security-and-acceptance)
- [Deferred work](#deferred-work)

## 10. Current Delivery Status and Roadmap

The current walking vertical slice is implemented for local development:

* persisted labeled-order creation and controlled transitions;
* authenticated, tenant- and location-authorized staff operations;
* backend-mediated self-hosted ZITADEL email/password authentication, immediate
  public tenant registration, fixed manually shared manager/operator invitations,
  durable rolling browser sessions, password changes and local/global logout;
* on-screen customer QR codes and anonymous tracking through REST;
* customer-only SSE invalidation through Redis Pub/Sub with REST
  reconciliation;
* customer PWA manifest, order-aware first-launch, last-destination restoration,
  active-only IndexedDB snapshots, in-app QR scanning, per-order tracking
  removal, generated Serwist service worker, explicit offline fallback,
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
* enabled, disabled, and archived member Accounts with statusless retained
  Location Assignments, nondisclosing authentication eligibility, and
  irreversible Account Delete;
* versioned external order commands with idempotent creation and desired-state
  updates;
* one channel-neutral transactional order outbox with Spring API background
  jobs for independent single-attempt webhook and durable retrying Web Push
  delivery;
* one API configuration with externally supplied private keys;
* repository-owned local setup for configuration, complete key validation,
  restrictive permissions, and explicit existing-key handling;
* shared hostname-routing NGINX HTTPS ingress, unbuffered SSE, internal health
  paths, health-gated dependencies, persistent PostgreSQL/provider credentials,
  and nondurable Redis;
* GitHub Actions validation for pull requests and `main`.

### Implemented behavior and development schema

The panel removes terminal orders from the active queue after an accepted
transition and shows the customer QR code without a separate tracking-link,
printing, or download workflow. The database starts empty. Because the local
database was discarded while developing these increments, schema changes are
consolidated in the initial Flyway migration rather than compatibility
migrations.

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

The ZITADEL session design replaces the transitional Google/Clerk work. Source
and automated provider-isolated checks cover the accepted flow; live acceptance
against the pinned provider and hosted gateway remains outstanding. Direct
member creation through `POST /api/accounts/v1` remains
unavailable; managers/operators join only through fixed Account Invitations.

The implemented administrative-lifecycle increment provides
tenant-administrator Location management, validates and completes account
registration before requiring first-location creation on the main page,
protects the last non-archived location from deletion, retains the reusable zero-enabled-location creation flow in
Orders, standardizes the managed-resource lifecycle vocabulary, adds Account
archival through Delete, and applies the Location cascades and contracts
specified in [location lifecycle](locations.md) and [HTTP contracts](http-contracts.md), across the schema, API, panel, and automated verification.

### Outstanding security and acceptance

Outstanding security and acceptance work:

* establish a verified tenant and location database security context and enable
  PostgreSQL Row Level Security for every tenant-owned or ownership-derived
  table;
* verify hosted gateway client addressing, throttling and origin-bypass blocking
  through Cloudflare; extend throttling when future recovery or linking routes
  are introduced;
* operate and patch ZITADEL, rotate backend
  service credentials, and
  provide externally managed webhook-secret encryption keys,
  VAPID signing keys, and push-subscription encryption keys, with documented
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

Scheduled backups, retention/recovery targets and restore rehearsals are deferred
until real users or valuable data under the
[operations policy](routing-releases.md#development-operations-policy). Disk-space,
memory-pressure and public HTTPS availability alerts are also deferred in favor
of native Lightsail status, CPU and CPU burst-capacity alarms. No custom host
scripts, monitoring agents or external monitoring service are introduced.

Deferred operational and product work includes live staff queue
synchronization, order archives and search, printable QR artifacts,
tracking-reference expiration, session-management UI, additional
administrators, CAPTCHA, MFA,
passkeys, webhook DLQ
inspection and alerts, automatic webhook retry or redelivery, strict delivery
ordering, application-owned install prompts, and native mobile variants. Any of
these requires an explicitly approved increment and synchronized changes to
the [canonical requirements](../REQUIREMENTS.md).
