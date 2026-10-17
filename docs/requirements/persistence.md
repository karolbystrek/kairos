# Persistence requirements

## Persistence requirements

The database schema must include tables covering the following concepts. Names and nonessential columns are implementation decisions.

* **Tenants:** stable identity and integration configuration.
* **Locations:** physical restaurant belonging to one tenant, with a required
  trimmed human-readable display name, enabled, disabled, or archived status,
  operational information, and an immutable IANA time-zone identifier chosen
  at creation. Delete archives the row rather than physically
  removing its stable identity or historical relationships.
* **Accounts:** stable UUID, normalized globally unique email, unique immutable
  staff identity subject (`provider_subject`), optional authentication cutoff, tenant ownership,
  tenant role, enabled/disabled/archived status, and audit timestamps. Archived
  email and identities remain reserved until the retention purge. The identity subject is required.
* **Staff identities:** opaque subject, unique normalized email, PBKDF2 password
  hash, email-verified flag and the hashed current verification code with its
  expiry ([staff credentials](staff-credentials.md)).
* **Browser sessions:** standard Spring Session JDBC identity, creation/access
  timestamps, rolling inactivity expiry, Account principal index and server-only
  identity-subject session attributes. No custom refresh-session table.
* **Account Invitations:** separate authority to create one member account,
  including stable identity, normalized recipient email, direct tenant ownership,
  fixed target location and
  manager or operator role, issuing account, SHA-256 hash of a single-use
  32-byte random Base64url bearer credential, `PENDING`, `REDEEMED`,
  `REVOKED`, or `REPLACED` lifecycle state and internal revocation reason, seven-day absolute
  expiry, and timestamps. Expiration is derived from the deadline rather than
  persisted as an additional lifecycle state. An invitation fixes its recipient email
  but contains no password and is not itself an account or a reservation of a
  globally unique Account email. Multiple pending
  invitations may share a target location and role; active entries are unique per
  tenant and normalized recipient email. Replacement retains the previous row
  as `REPLACED` and creates a fresh token/lifetime. Revoked/replaced invitations are
  deleted 30 days after revocation/replacement and pending ones 30 days after expiry; a
  redeemed invitation is deleted with its issuing or redeeming account, or with
  its location (see [retention purge](resilience.md#retention-purge)).
* **Location assignments:** relationship between a non-admin tenant Account and its
  accessible location, including a manager or operator role. The assignment has
  no independent lifecycle status; effective access requires both its Account
  and Location to be enabled. The current account model permits at most one
  assignment per account; the relationship remains normalized so that this
  cardinality can be changed explicitly in a future migration. Archiving an
  Account retains the assignment as its historical account-to-location
  relationship.
* **Orders:** public tracking identity, owning location, immutable daily number and local business date, optional customer-visible text label, ordered menu-item snapshot of names and quantities, current state, and lifecycle timestamps. Tenant ownership is derived through the location.
* **Anonymous order measurements and hourly aggregates:** self-contained pending inputs and tenant/location/UTC-hour counts with versioned mergeable duration summaries; no order, tracking, user, label or payload association. Tenant deletion cascades both tables while location purge preserves them. See [anonymous order analytics](order-analytics.md).
* **Anonymous usage hourly aggregates:** tenant, optional retained location UUID, UTC receipt hour, predefined action and additive count, without caller identities or raw input. Tenant deletion erases these aggregates and location purge preserves them; see [usage analytics](usage-analytics.md).
* **Platform administrators:** manually granted account references, written only by the database owner and cascaded with account deletion; see [platform authority](analytics-reporting.md#platform-authority).
* **Order history:** order association, resulting state, acceptance time, and the initiator category and identity when known; records are append-only and have an unambiguous order. Initiator categories distinguish users, external integrations, and system actions without coupling history to one authentication mechanism.
* **Daily order-number counters:** one row per location and local business date holds the last allocated number. Creation atomically increments it in the order transaction, so concurrent creation never duplicates a number; a unique order constraint on location, business date and number backs this up. Counters are removed with their location.
* **Menus:** the optional menu of each location with its ordered item names; see [menus](menus.md#menu-storage-and-access).
* **External Integrations:** tenant association, normalized name, and enabled, disabled, or archived lifecycle state. Archived integrations and webhook subscriptions free their names.
* **API Keys:** immutable scopes, expiration, hashed one-time secret material and
  location grants on the named key, except for the removal of an archived
  Location by the Location Delete cascade; deletion time and an approximate
  last-used time. Deleted keys are retained for order-history attribution and
  only active keys reserve their normalized name.
* **Webhook subscriptions:** integration association, normalized name, destination, enabled/disabled/archive state, selected locations, and selected event types.
* **Webhook signing-secret versions:** encrypted recoverable signing material, rotation overlap, and retirement state. Encryption keys are externally managed and never generated by the application or image.
* **Transactional email deliveries:** durable queued, sent or failed state, stable
  provider idempotency key, attempt count and next-attempt time. Pending message
  contents (including verification codes and invitation tokens) are encrypted
  with a dedicated externally mounted 32-byte key. Terminal states erase the
  encrypted message contents; unreferenced terminal rows are purged after 30
  days (see [retention purge](resilience.md#retention-purge)).
* **Order outbox events:** immutable event identity, order/location and tracking association, exact serialized webhook payload, resulting order state, occurrence time, and independent webhook and customer-push fan-out state.
* **Webhook deliveries:** recipient-specific captured destination, payload, signing versions, claim state, attempt count and next attempt time, the latest attempt outcome, bounded response details, and durable success or dead-letter state.
* **Customer push subscriptions:** a browser-context capability with hashed
  endpoint, encrypted recoverable endpoint and authentication secret, client
  P-256 public key, VAPID-key fingerprint, expiration when supplied by the
  browser, and last-observed time.
* **Customer push enrollments:** the many-to-many association between a complete
  browser push subscription and active order tracking references.
* **Customer push deliveries:** one mutable retry row per outbox event,
  subscription and notification kind, including the privacy-minimal payload, freshness deadline,
  claim state, attempt count, next-attempt time, bounded outcome details, and
  accepted, superseded, canceled, expired, or terminal dead-letter state.
* **External order creation identity:** integration-and-location-scoped idempotency value and canonical creation-input fingerprint associated with the created order.

Tenant ownership may be direct or derived through an unambiguous relationship such as order to location to tenant. Tables must carry enough association for RLS enforcement and efficient access checks without duplicating ownership data by default. Passwords and External Integration bearer secrets must never be stored in plaintext. Standard Spring Session JDBC stores opaque session IDs and identity references, and `staff_identities` stores password and verification-code hashes; protect the database and backups as authentication-bearing data.

Order labels are optional, trimmed, single-line text of at most 80 characters; blank input is stored as no label. Labels preserve casing, may contain ordinary Unicode text, and are not unique; the daily number identifies the order. Labels, numbers and items cannot be edited after order creation.

Ownership columns and related order/location/tenant, event/order and webhook
recipient/signing-version relationships are constrained consistently. Location
live-name uniqueness uses a partial index on `(tenant_id, normalized_name)` for
non-archived rows; no duplicate live-name column is stored. Normalized location
names allow Unicode normalization expansion. Order-history initiator constraints
reject partially missing identities while retaining intentionally identity-free
records. Push deliveries omit unused endpoint fingerprints and service origins;
retained deliveries derive ownership through event/order even when their
subscription reference becomes null.

Owner and runtime roles are separate and non-bypass; runtime cannot own objects,
create schemas, truncate tables or inherit the owner role. Session tables receive
explicit infrastructure privileges. Narrow SECURITY DEFINER helpers use a fixed
safe search path, qualified relations, revoked PUBLIC execution and named runtime
grants. PostgreSQL RLS applies command-specific read/write policies to direct and
derived ownership without adding duplicate tenant columns everywhere. Application
startup verifies role flags, ownership, expected policy/helper revision and grants.
Migration credentials remain in the startup process, so this protects ordinary
runtime database queries rather than a fully compromised API process.

Business workflows and entity reads/writes remain in Java and Hibernate.
PostgreSQL-specific context, minimal credential projections, worker discovery and
capability aggregate/cancellation operations stay in persistence infrastructure.
Registration and invitation redemption use exact new-identity/bearer scopes;
they do not execute privileged database business workflows.

Fresh PostgreSQL entrypoint initialization provisions restricted
roles before Spring Flyway applies V1. See
[database roles and initialization](../deployment/DATABASE.md), including the
one-time reset required for the consolidated pre-launch baseline. Volume resets are
separately authorized operator actions.

Locations retain an optional Google review link and its configuration identity.
Completion captures the due time and configuration identity on the order; later
configuration changes/disablement invalidate outstanding follow-ups without
keeping terminal notification enrollments. Review invitations create no push
deliveries.
