# External Integration API and webhooks

## Contents

- [External Integration API and webhooks](#external-integration-api-and-webhooks)
- [Contents](#contents)
- [3.4 External Integration API and webhooks](#34-external-integration-api-and-webhooks)

### 3.4 External Integration API and webhooks

An External Integration is a named, tenant-owned representation of a
third-party system. A point-of-sale system is the first use case rather than a
distinct credential model. Tenant administrators can create, rename, disable,
re-enable, and archive integrations after signing in. Tenant registration
creates no integration, API Key, subscription, or secret.

An integration may independently own API Keys and webhook subscriptions. Each
stable, named API Key has immutable scopes, access to one or more current tenant
locations, and an optional immutable expiration. `orders:write` includes
`orders:read`. Keys can be revoked immediately and irreversibly. Rotation
creates a new secret version while the immediately preceding version remains
valid for a configured 24-hour grace period. The full high-entropy
secret is revealed once and only a non-reversible hash is stored. One-time
secret presentation keeps the copy control beside the secret, temporarily
replaces it with a completion mark after copying, and uses a right-aligned
**Confirm** action to leave the locked state. API Key version history opens in
a focused modal rather than expanding inside integration configuration.

An API Key's location grants cannot be edited through API Key management.
Irreversible Location Delete is the sole cascade that removes an archived
location from an existing key; a key left without a location is revoked.

The versioned external order API supports cursor-paginated listing, direct
lookup, idempotent creation, and idempotent desired-state updates. Authorization
always intersects the presented key's scopes and locations. Direct lookup
outside that intersection returns `404`. Creation requires an
`Idempotency-Key`, scoped to integration plus location, and returns the original
order for an exact replay or `409` when the same value is reused with different
creation input. The value is opaque, case-sensitive, and limited to 255 UTF-8
bytes. Lists use an opaque cursor over descending creation time and order
identity, default to 50 results, and accept at most 100. A same-state status
request changes no history and emits no event.

Webhook subscriptions independently select at least one location and supported
order event type. Creation starts with every supported event type selected so
the administrator can remove only events the recipient does not need. A new
subscription is disabled until its one-time signing
secret has been copied and the recipient configured. Each delivery uses a
CloudEvents 1.0 structured JSON body containing a complete external order
snapshot, `urn:kairos:orders` as its source, and `orders/{orderId}` as its
subject. It is signed over its timestamp and exact raw body with HMAC-SHA256.
The external REST representation includes the customer tracking reference;
webhook snapshots omit that reference together with idempotency and other
external-correlation values.
Signing-secret rotation has a fixed 24-hour overlap, during which new deliveries
carry signatures from both the current and immediately preceding versions. The
`Kairos-Signature` header uses
`t=<epoch-seconds>,v1=<lowercase-hex>[,v1=<lowercase-hex>]`, and each signature
covers the UTF-8 bytes of `<epoch-seconds>.<exact-body>`.

Order changes create one immutable, channel-neutral outbox event in the same
database transaction as the order and history mutation. Independent background
pipelines inside the Spring API fan out recipient-specific webhook and
customer-push delivery rows.
The webhook pipeline attempts each delivery once at the application-policy level.
Any redirect, timeout, network failure, or non-`2xx` response is durably
dead-lettered; v1 performs no automatic delivery retry. API process crash recovery
may result in duplicate delivery, so consumers deduplicate by the stable
CloudEvent ID and reject stale order snapshots by state and timestamp.

Production webhook destinations require HTTPS and public network addresses.
DNS is revalidated for delivery, redirects are not followed, and connection,
response, and response-body handling are bounded by a fixed ten-second total
HTTP timeout. Only an operator-controlled local profile may relax HTTP and
private-address restrictions.
