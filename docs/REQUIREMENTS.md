# Kairos Architecture and Requirements

## 1. Purpose and Scope

Kairos is a multi-tenant virtual pager system for restaurants. A tenant represents a customer organization, such as an independent restaurant or restaurant chain, and owns zero or more physical locations. A newly registered tenant has no location until its tenant administrator creates one. A customer scans a QR code assigned to an order and opens a lightweight web application that displays the current order state and receives real-time updates. Restaurant staff manage orders through a separate administrative panel. External systems, initially point-of-sale systems, can create and update orders through a versioned REST API and receive webhooks.

Kairos replaces failure-prone physical restaurant pagers without requiring a
customer to install a chain-specific native application for a short-lived
transaction. It remains independently usable by restaurant staff while
offering an optional, language-agnostic integration boundary for point-of-sale
systems.

The core system consists of three independently deployable applications:

* **Customer application:** anonymous, mobile-first Next.js PWA for order tracking.
* **Staff panel:** authenticated Next.js application for queue management and QR-code generation.
* **API:** Spring Boot application responsible for all business rules, security, persistence, real-time communication, and external integrations.

Native mobile applications, App Clips, and Instant Apps may be added later, but
the browser experience must work without installing the customer PWA. Web Push
is an optional enhancement that requires explicit customer consent; foreground
REST and SSE tracking remains the primary experience.

## 2. Technology and Ownership Decisions

### 2.1 Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3,
and Lucide React. They remain separate because they serve different audiences
and have different authentication, PWA, caching, and release concerns. The
customer application uses a custom TypeScript service worker built with Serwist
in configurator mode and owns its offline snapshots in IndexedDB.

Both frontends share one coherent, Apple-inspired web design language while
retaining browser-native behavior. They use the same semantic visual system,
interaction principles, and quality standard; differences in information
density reflect the customer and staff tasks rather than different brand or
role-specific styling. The **Kairos** name is the only visual-brand artifact
that must be preserved during the redesign. Icons, colors, typography,
materials, and other visual assets may be replaced.

The visible standalone **Kairos** wordmark uses one shared monospace brand
role in both frontends while interface and body text remain in the system
font. On the anonymous staff sign-in and onboarding surface, the wordmark is
centered and visually dominant without a descriptor or explanatory sign-in
sentence beneath it.

The shared visual character is direct, neutral, and hospitality-oriented
without restaurant-themed decoration. Light appearance uses a true white page,
light-gray secondary regions, and one restrained light-blue selection/accent;
dark appearance uses black, dark gray, and the equivalent blue. Semantic error
or destructive color is reserved for the states that require it. Hierarchy
comes primarily from typography, alignment, spacing, and separators rather
than visible containers. Routine content must not be wrapped in cards, bordered
boxes, or decorative materials. Elevation is reserved for genuinely floating
layers such as menus and modal sheets.

Both frontends use one CSS-defined foundation before component styling: a
4-point spacing basis with 4, 8, 12, 16, 24, 32, and 48-pixel steps; 32-pixel
compact, 40-pixel regular, and 44-pixel touch control sizes; small 6-pixel,
medium 10-pixel, and large 14-pixel corner radii; a restrained system-font type
scale; and one-pixel separators. Pill geometry is limited to controls whose
meaning requires it. Staff controls retain comfortable touch targets while
queue content remains compact enough for an operational tablet workspace.
Text inputs and selectors use the regular control height by default. When an
input and actions share an inline control row, that row owns one semantic height
for every peer so their alignment can be changed centrally without per-control
overrides.

Both frontends provide Light, Dark, and System appearance preferences, with
System as the default. Each browser stores its own preference; appearance is
not account data and does not synchronize between devices. The appearance
trigger keeps the same rounded icon-control geometry while its menu is open and
uses one valid interactive element rather than nested controls. The staff panel is
designed primarily for tablet use,
especially 11- to 14-inch touch-and-pointer devices, while remaining complete
and responsive on desktop and mobile. Mobile staff use is expected to be
infrequent. Staff roles use the same interface language and workspace styling;
capabilities still determine which operations are available. Product copy is
English-only in the current scope, but layouts must tolerate longer future
localized text without depending on short English labels.

Neither frontend uses a persistent global top bar. Customer pages
place only the utilities relevant to that page in a quiet trailing control
group; the tracking view aligns Home, Notifications, and Appearance on one
level. On tablet and desktop, staff workspace navigation is a flat, rounded
segmented control centered independently of the trailing Appearance and Sign
out utilities on the same level. It uses only a solid secondary background and
selected segment, without glass effects, decorative borders, or shadows.

Interactive staff cards across Orders, Locations, Accounts, and Integrations
use one reusable Panel Card anatomy: a direct full-card target, primary identity,
supporting metadata, and an optional trailing accessory or action group. They
share the medium corner scale, page surface at rest, one-pixel separators, and
an 8-pixel inter-card rhythm with immediate hover, keyboard-focus, and pressed
feedback. Selected collection
cards add the restrained blue selection surface without a decorative leading
edge. Cards keep identity and compact accessories inline across phone, tablet,
and desktop widths; essential text reflows instead of being removed.

Routine interactive surfaces in both frontends use one shared short motion
system: a restrained scale lift on precise-pointer hover, immediate compression
while pressed, and a subtle spring-like return on release. Stateful spatial
transitions that communicate a changed location or support direct manipulation
remain purposeful exceptions. Customer order rows use the same medium corner
scale and interaction treatment as staff cards while preserving the customer
swipe-to-remove gesture. Pointer capture begins only after horizontal swipe
intent is established so an ordinary tap remains native link navigation.
Reduced-motion preferences retain non-spatial state feedback while suppressing
the interaction scaling.

Transient notices in both frontends use the shared flat secondary surface and
medium corner scale. Their status indicator is centered against the complete
text block, and dismissible notices keep a trailing, icon-only Close action in
a stable column on desktop and mobile.

Action controls are icon-first where a familiar symbol communicates their
meaning, including Home, Scan, Notifications, Appearance, QR, More, Close, and
Switch camera. Primary workflow transitions, authentication, and destructive
confirmations retain concise visible text, usually paired with an icon, because
those actions do not have an unambiguous universal symbol. Every icon-only
control has an accessible name and, where pointer input is expected, a tooltip.
Both frontends source interface glyphs from Lucide React so repeated actions
share one stroke, proportion, and optical language. Application code does not
define handwritten inline SVG icon components.

HeroUI is the component library for both applications. Zod validates frontend-owned form input and server-sent event payloads. Native `fetch` is the HTTP transport inside small handwritten REST request modules. SWR manages REST-backed client state in both frontends where caching, request deduplication, mutations, focus revalidation, or reconnect revalidation applies. React effects are reserved for synchronization with external systems rather than routine REST request orchestration.

The Spring API retains domain authority. Frontend controls and redirects provide user experience, while the backend enforces authorization.

### 2.2 Backend

The backend uses Java 25 and Spring Boot 4. It is the only system of record and owns:

* order lifecycle and transition rules;
* staff authentication and authorization;
* tenant, location, and staff-access resolution;
* PostgreSQL persistence and migrations;
* Server-Sent Events publication;
* Redis-backed coordination and pub/sub;
* External Integration authentication, REST access, and webhooks;
* transactional order-event outbox processing;
* anonymous customer notification enrollment and durable Web Push delivery.

Browser-facing API requests go directly to the Spring API origin through the
shared NGINX gateway. The local HTTPS environment uses credentialed CORS scoped
by frontend origin and browser resource family. Next.js route handlers are
limited to frontend rendering concerns.

### 2.3 API contracts

REST is the integration boundary for frontends and third-party systems. During the first walking vertical slice, the endpoints are implemented as ordinary Spring MVC controllers and each frontend uses small handwritten TypeScript response types with native `fetch`, wrapped by SWR in Client Components that need server-state synchronization. The slice does not expose an OpenAPI document or generate REST clients. Next.js Server Actions and proxy route handlers do not replace or wrap the Spring REST boundary.

External Integration REST and webhook contracts remain language-agnostic and
use versioned resource families. OpenAPI publication, rendered reference
documentation, generated SDKs, and automated public-contract checks remain the
next integration increment. Introducing them must not move business rules or
API ownership out of the Spring application.

## 3. Functional Requirements

### 3.1 Order lifecycle

An order follows a controlled lifecycle:

1. **In preparation:** staff or an authorized External Integration creates an accepted order for an accessible location. The API immediately places it in preparation and returns its customer-visible label and QR-code tracking reference.
2. **Ready for pickup:** staff or an External Integration marks the order ready and connected customer pages refresh their visible state.
3. **Completed:** the order is collected, the live session ends, and later scans show a terminal state.
4. **Canceled:** the order is canceled before completion, connected customer pages refresh, and the live session ends.

There is no separate `CREATED` state: every created order is automatically accepted into `IN_PREPARATION`. From there it may become `READY` or `CANCELED`; a ready order may become `COMPLETED` or `CANCELED`; completed and canceled orders are terminal. The customer cannot request transitions.

The backend validates every transition regardless of whether it originates from the panel or an External Integration. Every accepted resulting state is recorded in an append-only history. The prior state is derived from the preceding history entry, with the first `IN_PREPARATION` entry representing creation.

### 3.2 Customer application

The customer application must:

* open directly from an order-specific QR code without login or installation;
* display the current order state before establishing real-time communication;
* open an anonymous Server-Sent Events stream scoped only to the referenced order;
* validate every received event before using it as a signal to revalidate the authoritative REST state;
* rely on the browser's native `EventSource` reconnection and fetch the current state through REST whenever the stream opens or reopens;
* poll REST approximately every 15 seconds while the event stream is disconnected, and stop fallback polling when it reconnects;
* reconcile through REST on focus and browser connectivity restoration;
* make an active order's current status the dominant content and its immutable
  label a quieter identifier, express the current state once as a centered
  message without a duplicate status label, decorative status symbol, or
  explanatory paragraph, show a status timestamp only when the displayed data
  is explicitly stale, and rely on automatic reconciliation rather than an
  always-visible manual refresh;
* provide clear terminal views for completed, canceled, or unknown orders;
* keep completed and canceled tracking references readable without an expiration
  policy in the current scope;
* use a service worker for an installable shell, explicit offline fallback, Web
  Push handling, and application badges without treating cached order REST
  responses as authoritative;
* support current Chrome on Android, Safari on iOS and iPadOS, and current
  desktop Chrome and Firefox progressively, while preserving the ordinary web
  experience when an optional PWA API is unavailable.

The customer application is installable as one globally branded **Kairos** web
application while remaining fully usable without installation. Installation is
browser- or operating-system-initiated; Kairos does not show its own install
prompt in the current scope. The installed application uses standalone display
mode, the shared Light, Dark, or System appearance preference, and no
orientation lock. Android Chrome and iOS
Safari are the required installation targets, while desktop browsers must
continue to provide the ordinary web experience.

The manifest uses `Kairos Order Tracking` as its full name and `Kairos` as its
short name. Its stable application ID and scope cover the complete customer
origin. Home uses the ordinary root start URL. An order page exposes an
order-aware manifest with the same stable identity and a start URL that enters
through Home with only the tracking reference required for bootstrap.

An installation initiated from an order page opens that order on the first
installed launch while retaining one stable Kairos application identity and
origin-wide scope. Each browser or installed-app context subsequently retains
its own local recently tracked orders. The contexts are not synchronized with
one another, with another device, or through the backend. This local collection
does not establish customer identity or order ownership.

The local collection uses IndexedDB and retains only distinct active
`IN_PREPARATION` or `READY` orders. Reopening an order refreshes its stored
label, status, and server-provided `updatedAt` snapshot and moves it to the front
of the collection. A terminal REST response or accepted push transition removes
the order from that active collection. A short-lived terminal tombstone prevents
an older response or push from resurrecting it. Home renders these stored
summaries without opening SSE streams; when notifications are enabled it may
reconcile the current browser push subscription and active enrollments with the
API. Selecting an entry opens its tracking page, where the normal authoritative
REST and SSE flow resumes.

After the one-time installation launch, opening the installed application
directly restores the last stable destination. It reopens that order only when
its stored status is `IN_PREPARATION` or `READY`; otherwise it opens Home. Home
displays only one horizontally and vertically centered scanner action when
IndexedDB is empty, corrupt, unavailable, or inaccessible. Its label
and icon scale together as the empty page's clear primary focus, with the large
label above an oversized, still-dominant QR icon. The control has no resting
container and reveals its button surface on hover, keyboard focus, or press.
Active and terminal tracking views provide a quiet Home action.

Home labels its active local collection **Your orders**. Swiping an order card
left reveals a **Stop tracking** action, and the same action remains available
through an accessible per-card overflow menu. Stopping tracking removes only
that order's local snapshot and notification enrollment, updates the application
badge, and does not prevent a later explicit scan or reopening from tracking the
active order again.

Each Home order row is itself the direct navigation target for that order and
does not depend on explanatory copy telling the customer to select it.

Home opens a dedicated scanner view only after a direct customer action. The
scanner prefers the rear camera, allows switching when multiple cameras are
available, decodes locally, and accepts only customer-origin URLs whose exact
path is `/orders/<UUID>`. Invalid codes leave scanning active with an inline
error. Every camera track stops after success, cancellation, navigation, or
unmount. Camera failure directs the customer to the device Camera app. A valid
code scanned offline remains only in memory and can be retried after connectivity
returns; it is not added to **Your orders** until the order loads successfully.

Order REST endpoints are network-only in the service worker. The application
shell and a dedicated offline route are precached, while application code
explicitly reads last-known order snapshots from IndexedDB after a network
failure. An offline view must label its data and timestamp as last known and
must not imply that it is current. When no snapshot exists, the application
shows an offline explanation rather than manufacturing an order state.

Notification consent belongs to the complete Kairos PWA context rather than an
individual order. Kairos requests browser permission only from a direct user
action. On iOS and iPadOS outside standalone Home Screen mode, the control first
explains how to add Kairos to the Home Screen instead of calling the permission
API. A denied permission shows browser-settings guidance. Once enabled, every
locally active order is automatically reconciled as an enrollment for the
current browser push subscription. New active orders are enrolled silently, and
terminal orders are removed after their final notification delivery has been
materialized. The API limits a subscription to ten contexts per order.

The application exposes one persistent notification control in the top-right
corner of every customer view. Its icon distinguishes enabled notifications
from all off or unavailable states: a normal bell offers notification enabling,
while a crossed bell indicates that notifications are currently enabled and
the action will disable them. On order views, the Home action appears
immediately alongside it. Disabling notifications durably retires the current
subscription and all of its enrollments before removing the browser
subscription. Stopping tracking removes the selected active-order enrollment
but preserves the app-level notification preference for future orders. Both
disabling notifications and stopping notification-enabled tracking require a
network connection so the UI does not make a false backend-cleanup promise.
Browser-initiated subscription replacement is reconciled once from the service
worker and idempotently retried on the next application start if needed.

Transient in-app notification guidance aligns to the customer content width and
uses the same flat secondary surface and corner scale as the rest of the
interface. Every such message provides a trailing, icon-only Dismiss action
that clears the current message without changing notification settings.

Background notifications are generated for `READY`, `COMPLETED`, and `CANCELED`
transitions only. Their title is the global Kairos brand, their body describes
the state without disclosing the order label, and their click target is the
order route. Notifications use one replacement tag per order. The service worker
validates a versioned payload, deduplicates its stable event ID, and applies the
transition only when it is reachable from the locally stored state graph.
Malformed or unprocessable payloads produce a generic, privacy-preserving
notification. The foreground tracking page continues to use REST and SSE and
may issue one short vibration pulse for a newly observed transition when the
browser permits it; no custom notification vibration pattern or sound is used.

The application badge, where supported, is the number of locally active orders
enrolled for notifications. Kairos does not add Background Sync, Periodic
Background Sync, Screen Wake Lock, synthetic audio, or a separate background
polling promise. The service worker does not force `skipWaiting` or immediately
claim existing clients; an update activates through the browser lifecycle.

### 3.3 Staff panel

The staff panel must:

* offer public email/password registration that creates a tenant and its first
  administrator immediately, without email verification;
* require an authenticated internal account;
* show only locations and orders accessible to the account;
* allow tenant administrators to switch between locations or view an aggregate queue;
* create labeled orders for an accessible location and display their customer QR codes;
* offer automatic labels by default and allow a custom label before creation;
* display and refresh only active order queues by location;
* allow only valid order transitions permitted by the account's role;
* allow tenant administrators to issue account invitations for location
  managers and operators within their tenant;
* allow a location manager to issue operator account invitations only for the
  manager's assigned location;
* list manageable member accounts and allow their current status to be changed
  or the account to be deleted through irreversible archival within the same
  tenant and location authorization rules used for provisioning;
* allow tenant administrators to manage External Integrations, API Keys, and webhook subscriptions;
* allow tenant administrators to create, rename, disable, and delete their
  tenant's locations, with Delete presented as a destructive operation while
  retaining the location internally as archived;
* provide clear feedback for stale data, rejected transitions, expired sessions, and network failures;
* keep messages and administrative summaries user-oriented by omitting routine
  background-refresh narration, arbitrary backend error details, internal
  integration or credential identifiers, and raw permission tokens.

The staff experience keeps the active queue visually primary. Administrative
capabilities must not compete with frequent order creation, QR presentation,
or valid order transitions merely because they are available to the signed-in
account.

Orders, Locations, Accounts, and Integrations use one adaptive tab-style
navigation system: a centered rounded segmented control on tablet and desktop and bottom
navigation on narrow mobile layouts. Only authorized destinations are present,
but the shell and visual treatment do not vary by role. Appearance and Sign out
remain direct trailing utilities on the same level rather than peer workspace
destinations or commands nested in an account menu. Sign out requires an
explicit confirmation before the browser session is ended.

Locations appears between Orders and Accounts for a tenant administrator and
is absent for managers and operators. The Locations collection includes enabled
and disabled locations and omits archived locations. Operational selectors and
new Account Invitation, API Key, and webhook-subscription forms offer only
enabled locations.

The active queue uses adjacent **In preparation** and **Ready** sections on
tablet and desktop and the same two sections stacked vertically on mobile.
These sections use headings, counts, whitespace, and a shared separator rather
than large rounded lane containers. Order creation begins from one compact,
icon-only plus action immediately beside the **Orders** title. Its focused
sheet contains one optional label field whose
placeholder communicates the automatic default; leaving it empty requests an
automatic label and entering text requests a custom label, without a separate
mode selector. Successful creation continues directly into a large,
dismissible customer QR sheet; explicitly requesting an existing order's QR
uses the same presentation. The underlying queue remains visible to preserve
context. The Orders, Locations, Accounts, and Integrations creation actions
share one 44-pixel icon-control geometry, accessible name, and pointer tooltip beside
their respective page titles on every layout. The queue-location selector
remains an independent filter beneath the page header.

When a tenant has no enabled location, whether none have been created or every
existing location is disabled, Orders replaces its queue and creation control
with a direct message and **Create location** button. The button opens the same
focused location-creation modal used by the Locations destination without
changing the selected workspace destination. Successful creation closes the
modal, keeps Orders visible, and selects the new enabled location for the first
order.

The customer-QR sheet contains only the centered order label, a dominant QR
code, and the essential close control. It does not repeat queue status, elapsed
time, explanatory copy, or a redundant footer action.

Canceling an order requires a concise confirmation that identifies the order
and distinguishes keeping it from the terminal destructive action.

Each active-order card shows its elapsed waiting time, updated approximately
once per minute. The duration is supporting information: order label and next
valid action remain more prominent. Creator attribution is not shown. The
complete compact card remains a direct target for opening its customer QR code,
without a duplicate QR control. One trailing action group exposes two large
controls directly: the next valid status, labeled **Ready** or **Complete**, and
cancel. Cancel retains its order-specific confirmation rather than being hidden
in an overflow menu.

Locations, Accounts, and External Integrations use responsive
collection-and-detail navigation. New member-account access begins in a focused account-invitation
sheet that captures only the fixed location and manager or operator role;
the invited person supplies account identity and credentials when redeeming
the resulting link. The Accounts collection contains only non-archived created
accounts. A
trailing icon-only **Invitations** control opens a
focused HeroUI modal for unredeemed Account Invitations and their actions
without making invitations a peer workspace destination. The control remains
available with a zero count so its location does not change with state, has an
accessible name and pointer tooltip, and shows its positive pending count in a
HeroUI badge while omitting the badge at zero. The
modal is centered on tablet and desktop and adapts to a near-full-screen sheet
on mobile. Selecting an existing account opens its detail surface; an
integration detail uses a compact segmented control to switch between API Keys
and Webhooks. Account and integration details share one header anatomy: the
resource-type eyebrow and title or inline title editor remain leading, while a
regular-sized icon action rail remains trailing in a stable order. Integration
identity is read-only in the normal detail view and becomes editable only
through the Edit control in that rail; edit mode exposes direct confirm and
cancel controls in place of the title.
Contextual action labels omit a repeated entity name when the selected detail
already makes the target unambiguous. The new-integration sheet keeps its
single name field visually unlabeled and uses the direct **Enter integration
name** placeholder while retaining an assistive-technology label.

Account and integration collection cards use the shared Panel Card directly,
including its comfortable padding, minimum touch size, rounded geometry,
separator rhythm, focus and pressed feedback, and responsive reflow. They use
the restrained blue selection surface for the open resource with a stronger
blue-tinted hover state. Each card shows the resource status beneath its name;
the detail header does not duplicate that status. The selected account's
icon-only status action remains in the shared trailing header rail, exposes an
accessible name and pointer tooltip, and retains confirmation before disabling
access. A following icon-only Delete action remains available for an enabled or
disabled manageable account.

The Locations collection follows the same anatomy. Its enabled cards appear
first in display-name order, followed by disabled cards in display-name order,
with stable identity as the deterministic tie-breaker. Each card shows the
location status beneath its name. Selecting one opens a detail surface whose
stable action rail contains Edit, Disable or Enable, and Delete. Delete remains
visible but unavailable until the location is disabled. The detail uses no
time-zone editor or unrelated operational fields.

If confirmed location disablement is rejected because the location still has
active orders, the location remains enabled and the panel explains that those
orders must be completed or canceled first. A direct **View orders** action
opens Orders filtered to that location.

Accounts and Integrations remain available when the tenant has no enabled
location. Account Invitation creation is unavailable with guidance to create
or enable a location. External Integrations may still be created, while API Key
and webhook-subscription creation remains unavailable until an enabled location
exists.

Integration credential and webhook forms use neutral, task-specific
placeholders rather than suggesting a restaurant, point-of-sale product, or
other example identity.

Repeated administrative actions use one shared visual vocabulary across the
panel: Edit uses the pencil, Disable uses the prohibited-state symbol, Enable
uses the check, and Delete uses the trash symbol. Their compact icon-only
controls share the same size, corner treatment, accessible naming, and pointer
tooltip. Focused confirmation dialogs retain explicit text labels for
consequential actions.

Location, Account, External Integration, and Webhook Subscription share one
administrative lifecycle vocabulary: `ENABLED`, `DISABLED`, and `ARCHIVED`.
Enable changes a disabled resource to enabled, Disable changes an enabled
resource to disabled, and the UI action Delete irreversibly archives the
resource without physically deleting its identity or history. Each feature
keeps its own status type rather than depending on one cross-feature enum, but
database values, REST representations, frontend schemas, variables, and method
names use the same vocabulary. Domain aggregates expose `enable()`, `disable()`,
`archive()`, `isEnabled()`, `isDisabled()`, and `isArchived()` as applicable;
application and frontend request functions use `update...Status` naming.
Orders, Account Invitations, credentials, sessions, and delivery workers retain
their distinct domain-specific state machines.

Panel actions that immediately remove access, stop delivery, invalidate a
credential, retire an active secret version, cancel an order, or remove a
resource require a focused confirmation before the request is sent. This
includes disabling locations, accounts, integrations, and enabled webhook
subscriptions; revoking API Keys; retiring overlapping webhook secrets; and
deleting locations, accounts, integrations, or webhook subscriptions.
Re-enabling a resource and other non-destructive transitions do not add an
unnecessary confirmation step.

Location re-enabling is a deliberate exception because it changes every
assigned non-archived member account to `ENABLED`. Its focused confirmation
explains that effect before the request is sent. Location Delete is available only while the
location is disabled, requires the administrator to type the exact location
name, and explains that the location and assigned accounts disappear from
ordinary management and cannot be restored while historical orders remain
readable.

Account Delete requires a focused destructive confirmation and the exact,
exact normalized email before it can be submitted. The confirmation explains
that the account will be signed out, disappear from ordinary Accounts, lose its
pending invitations, and never regain access. A failed request keeps the dialog
and entered confirmation value in place, while success clears the selection if
the archived account was open and revalidates account and invitation state.

Disabling an integration requires confirmation and explains the immediate
credential and webhook effect. The archival operation is presented to staff as
**Delete**, requires a separate destructive confirmation, and remains disabled
until the administrator types the integration name exactly. The API continues
to archive the resource internally.

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
valid for a deployment-configured 24-hour grace period. The full high-entropy
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

## 4. Authentication and Security

### 4.1 Staff authentication

Staff authentication uses self-hosted ZITADEL through Spring's backend-only
Session API integration. Users enter email/password on the Kairos panel; no
hosted provider login redirect, browser SDK, social login, or JWT/refresh flow
is used. Kairos owns tenants, Accounts, memberships and authorization. All
Accounts belong to one tenant. Username, local password hashes, Platform
Operator, Tenant Registration Invitations, and verification staging are removed.

Public registration immediately creates one tenant and its first administrator,
without a location. Managers/operators register through manually shared member
invitations. Email verification and forgotten-password recovery are deferred;
provider email remains truthfully unverified and registration sends no email.
ZITADEL enforces password requirements. Initial provider policy is a minimum
of 12 characters without mandatory character classes or scheduled password
expiry; Kairos displays matching guidance, checks confirmation, and bounds input
at the provider's 200-character limit for email/password without duplicating
password policy.

Spring Session JDBC stores browser sessions durably in PostgreSQL. A secure,
HttpOnly, host-only `__Host-session` cookie with `Path=/` and `SameSite=Lax`
carries only an opaque session ID. Successful authenticated requests renew the
30-day inactivity lifetime and cookie retention. There is no absolute deadline
for active users; service restarts preserve sessions. Browser cookie deletion
can require login. Login replaces the previous browser session with a new ID.
Provider session/user IDs remain server-side in JDBC session attributes; the
provider session bearer token is discarded because the service account created
the session and can retrieve/terminate it without that token.
provider credentials and passwords never appear in browser
JavaScript, browser storage, API response bodies, URLs or logs. CSRF metadata is
separate: `__Host-XSRF-TOKEN` and the `X-XSRF-TOKEN` header protect unsafe browser
requests, including login/registration/logout. Credentialed CORS remains scoped
by frontend and resource family. No request-body/provider-response logging is
used for authentication.

Each protected staff request retrieves the ZITADEL session and requires the
expected provider user ID, successful password factor and absent/future expiry.
HTTP 200 alone is insufficient: password changes can remove the password factor.
Definitively invalid provider state invalidates the local session and returns
`401`; provider outages return `503` without discarding the session. Current
Account eligibility, role, Location Assignment and location status are checked
in PostgreSQL, with no permission cache, provider cache or event synchronization.
Email alone never grants authority. This deliberately adds a provider request
and database work to each protected request; optimize only when measured need
justifies the revocation delay or added complexity.

Ordinary logout invalidates the current browser session and attempts provider
session termination. Sign out everywhere deletes all indexed Kairos sessions for
the Account and records a cutoff to reject an in-flight stale session. Signed-in
password changes supply the current password to ZITADEL; success invalidates all
Kairos sessions, including the current one, and requires fresh login. Provider
sessions that have no remaining Kairos session grant no access to Kairos.

The panel performs no refresh or authentication replay. An unauthorized response
clears staff state; CSRF recovery is bounded to one bootstrap and retry, while
server errors leave the session available for retry. Staff SWR keys remain scoped
by Account ID and staff state is cleared on account change. Tabs share the normal
cookie; there is no custom tab locking or live auth synchronization. Other tabs
observe logout on their next protected request. Automated/background protected
requests count as activity; tracking mouse/keyboard activity is not required.

A **Location** is an enabled, disabled, or archived physical restaurant owned by
one tenant. A tenant may remain without any enabled locations, including before
its first location is created or after its last one is disabled or archived.
Only a tenant administrator manages the location lifecycle. The editable
location property in this increment is its
display name; its IANA time zone remains fixed at `UTC`. Enabled and disabled
location names are unique within a tenant after trimming and case
normalization. An archived location releases its name for reuse.

Disabling a location makes the location and everything scoped to it ineligible
for operational use. Every non-archived manager or operator account assigned to
that location is persistently changed to `DISABLED`; tenant-administrator and
archived accounts are never included in this cascade. Earlier authentication for
an affected account is invalidated by its authentication cutoff, and every pending Account Invitation that
targets the location or was issued by an affected account is revoked. A location
with an `IN_PREPARATION` or `READY` order cannot be disabled; the administrator
must complete or cancel every active order first.

Re-enabling a location persistently changes every non-archived account assigned
to it to `ENABLED`, including an account that was disabled independently before
the location was disabled. This deliberately favors one simple bulk lifecycle
over remembering or restoring prior account states. Archived accounts remain
archived, and pending invitations are not restored; staff issue new invitations
when needed.

Location disablement makes only that location ineffective within a
multi-location API Key or webhook subscription. The credential or subscription
and its other enabled-location grants remain usable. A configuration whose only
location is disabled remains stored but cannot perform location-scoped work or
receive new deliveries until that location is re-enabled.

Deleting a location archives it internally rather than physically removing its
identity or historical records. It also archives every non-archived assigned
member account, invalidates earlier authentication and revokes pending invitations, and removes those accounts from
the ordinary Accounts collection. Completed and canceled orders retain their
location association and remain anonymously readable through their existing
customer tracking references after location disablement or archival.

Only a disabled location can be deleted. Deletion is an irreversible archival
transition and an archived location cannot be restored. Archived member
accounts retain and reserve their normalized email addresses and provider subjects for
unambiguous historical attribution. They cannot authenticate or be restored.

Archiving a location permanently removes it from API Key location grants and
webhook subscription location selections. An API Key left without any location
grant is revoked, and a webhook subscription left without any location
selection is archived. Multi-location configurations retain their other
location relationships and lifecycle state.

An **Account Invitation** is a separate, single-use bearer capability that
authorizes creation of one person-oriented manager or operator account. It is
not an incomplete account and reserves no recipient email. It records the
owning tenant, fixed location and assignment role, and issuing account. Anyone
who possesses its link can attempt to redeem it, and the person who completes
the first successful redemption becomes the account owner. Redemption collects
the person's email, password, and confirmation. After verified sign-in it atomically creates an
enabled member account with an assignment to the enabled location while
consuming the invitation. No further administrator approval is required.

An invitation expires exactly seven days after issuance. Opening its
registration page neither reserves it nor extends that deadline. Multiple
pending invitations may target the same location and role. The full link is
returned only when the invitation is created, with a direct Copy action in the
panel; Kairos does not email it and persists only the SHA-256 hash of a token
containing 32 cryptographically random bytes encoded as unpadded Base64url. A
lost link is not recoverable and has no replacement operation. Staff issue
another invitation through the ordinary creation flow and explicitly revoke
the old one. The panel link uses
`/account-registration#invitation=<token>` so the secret is not sent
in the initial navigation URL, gateway logs, or referrer; the client reads the
fragment and supplies the token only in the API request body.

Expiration is derived from the authoritative `expiresAt` whenever invitations
are listed, previewed, or redeemed. PostgreSQL persists only `PENDING`,
`REDEEMED`, or `REVOKED`; a pending row whose deadline has elapsed is
effectively expired and cannot be redeemed. No background worker materializes
an `EXPIRED` state, and reads do not update the row merely to record the passage
of time. The exact audit time remains the stored deadline.

Pending invitations are omitted from the account collection and managed
through the dedicated focused surface opened from Accounts. Tenant
administrators may inspect or revoke every pending invitation in their tenant.
Managers may do the same for operator invitations to their own enabled location
regardless of which authorized manager issued them. Redemption
rechecks that the issuing account is enabled and remains authorized for the
fixed location and role; losing that authority makes its outstanding
invitations unusable. Disabling an issuing account revokes its pending
invitations in the same transaction and records an internal reason, while the
redemption-time check still protects future role or assignment changes.
Invalid registration input and email or provider-identity conflicts do not consume an
invitation, while concurrent redemption is
serialized so exactly one successful submission can create an account. An
existing account cannot redeem an invitation to gain another location or role.

The pending-invitations modal stays shallow rather than opening a nested detail
view. Each row shows role, location, creator, creation time, and absolute
expiration time, with creation time distinguishing otherwise identical
invitations. A trailing icon-only Revoke action uses the panel's established
prohibited-state symbol, an accessible name, and a pointer tooltip. Revocation
requires focused confirmation identifying the role and location because it
makes an already shared link unusable. Redeemed, revoked, and expired
invitations disappear from this modal after revalidation; redeemed accounts
then appear in the normal Accounts collection. Terminal invitation metadata
remains in PostgreSQL for audit but has no history interface in this increment.

The modal retains its structure while loading, uses a labelled progress
indicator, shows **No pending invitations** without duplicating the creation
action, and keeps failures in context with a user-oriented message and Retry.
Opening moves focus to its heading or first meaningful control, closing returns
focus to the **Invitations** control, and every row action remains operable by touch,
pointer, keyboard, and assistive technology. Mobile adaptation preserves the
same information and actions without horizontal scrolling.

The invitation count and modal use account-scoped SWR state. They revalidate
after creation or revocation and on focus and reconnect, and determine pending
eligibility against the server-provided timestamps. This increment adds no live
staff event stream for invitation changes.

Each Account belongs directly to one tenant and represents one staff
person, who may use that Account on multiple devices. A tenant administrator has
tenant-wide access; a location manager or operator has at most one location
assignment. Tenant administrators may issue manager or operator invitations
for locations in their tenant. Location managers may issue only operator
invitations for their own enabled location, and operators cannot issue
invitations. Additional tenant-administrator invitations remain outside this
increment.

Deleting a manageable member Account is an irreversible archival operation,
not physical row deletion. It removes the account from ordinary management,
invalidates earlier authentication and revokes every pending
invitation it issued, and retains its stable identity, provider subject, email,
and historical attribution. Archived email addresses remain reserved,
and an archived account cannot authenticate, be enabled, or be restored. Delete
may transition an enabled or disabled account directly to archived and applies
all shutdown effects atomically.

Account Delete uses the same authorization boundary as account status
management. A tenant administrator may delete a manageable member account in
its tenant. A location manager may delete only an operator assigned to that
manager's enabled location. An administrator account, the acting account, a
peer manager, and another location's operator are never valid targets.


The invitation registration form displays fixed location/role context and asks
for email, password, and confirmation. Signed-in users must explicitly sign out
first; the fragment is preserved while doing so. Successful registration creates the
Account and assignment in the same transaction as invitation consumption, then
issues cookies and opens Orders. Known unusable links have identity-free
terminal problems; unknown or malformed tokens do not disclose Account identity.

One browser profile represents one signed-in Account because tabs share cookies.
Same-account tabs are best effort, with no live UI synchronization or cross-tab
Web Lock. Different Accounts require separate profiles, private contexts, or
devices. The primary workspace supports administrator location switching and
aggregate views without depending on tabs.

Before public deployment, a non-bypassable gateway must rate-limit login,
registration, invitation redemption, authenticated password changes, and future
password recovery. Future email verification will not substitute for throttling.
Social login, MFA, and additional administrator invitations remain deferred.

### 4.1.1 Provider deployment and registration consistency

Compose pins ZITADEL v4.19.4, uses its own database/user on the existing
PostgreSQL server, and keeps the provider on the private data network without a
public port. A separate one-shot bootstrap provisions a Kairos service account
with the standard `IAM_LOGIN_CLIENT` and organization `ORG_USER_MANAGER` roles.
The bootstrap administrator PAT is not mounted into the API; only the service
PAT is mounted read-only. These provider roles are infrastructure permissions,
not restaurant roles. Kairos restaurant tenants do not become provider orgs.
The provider's supplied Login UI is unnecessary for this custom Session API flow.
Start with one instance and accept planned maintenance outages; HA is deferred.

`setup.sh` prepares the five application secrets including a stable 32-character
ZITADEL master key, preserving that key when replacing webhook/push material.
The provider needs the same master key for as long as its database is retained.
API PATs expire after one year and require operational rotation. Provider data,
master key and private credential volumes need backups and restricted access.
No SMTP provider is configured in this increment.

Validate invitation/email availability before provider provisioning. Create and
password-authenticate the provider identity, then commit the tenant/admin or
invitation/member transaction locally. Recheck invitation eligibility and consume
it atomically with the Account and assignment. On local failure, terminate the
new provider session and attempt deletion of only the newly provisioned identity.
No distributed transaction, queue or retry worker is introduced. A provider
outage during cleanup can leave an orphan identity requiring operator cleanup;
never attach an existing identity solely because its email matches.

Treat Kairos as a fresh, never-deployed repository until the user changes that
policy: no existing-account/password migration or backward compatibility.
V1 contains the accepted initial schema, including standard Spring Session JDBC
tables. Do not introduce transitional migrations solely for development data.
Local resets remain user-owned actions and are not performed automatically.
See [authentication setup](authentication-setup.md) for operational steps.

```mermaid
sequenceDiagram
    participant Panel as Kairos panel
    participant API as Spring API
    participant Provider as ZITADEL
    participant DB as PostgreSQL
    Panel->>API: Email/password + CSRF
    API->>Provider: Create session with user/password checks
    API->>Provider: Read session and verify factors
    API->>DB: Resolve eligible Account; save JDBC session
    API-->>Panel: Current Account + HttpOnly session cookie
    Panel->>API: Protected request + session cookie
    API->>Provider: Validate provider session
    API->>DB: Check current eligibility/permissions
    API->>DB: Execute authorized operation; renew idle expiry
    API-->>Panel: Response + renewed cookie retention
```

Validate unverified-email password login and provider disable/delete/password-
change/session-termination semantics against the pinned provider release before
public deployment. HTTP contract and provider-isolated tests do not replace this
live acceptance. Primary contracts: [Session API](https://zitadel.com/docs/guides/integrate/login-ui/username-password),
[session validation](https://zitadel.com/docs/guides/integrate/login-ui/session-validation),
and [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).

### 4.2 Customer access

Customer order tracking is anonymous. QR codes use high-entropy, unguessable order references and expose only the minimum customer-facing order information. Possession of a tracking reference grants read-only access to that order and never authorizes staff operations or access to tenant data.

Customer notification enrollment is also anonymous and treats the complete
browser push subscription capability as its credential: endpoint, P-256 client
public key, and authentication secret must all match for mutation or removal.
Unsafe notification requests retain normal CSRF protection. Enrollment requires
each high-entropy tracking reference and does not create a durable customer
identity or reveal whether an unrelated order exists.

Push endpoints must use HTTPS and resolve to public network addresses in
production. DNS is revalidated for delivery, pinned for the connection,
redirects are not followed, and fixed timeouts and response limits apply. Only
an operator-controlled local profile may relax the public-address restriction.
The endpoint and authentication secret are encrypted at rest with an externally
managed key; the endpoint is additionally stored as a non-reversible hash for
lookup. Logs, error responses, and metrics must not expose complete subscription
endpoints or key material. VAPID signing keys are externally managed and never
generated by the application or image.

### 4.3 External Integration access

External clients authenticate only with an API Key version secret in the
standard `Authorization: Bearer` header. URL and query-parameter credentials are
not accepted. Authentication establishes the owning tenant, External
Integration, stable API Key, exact API Key Version, scopes, and location grants.
Disabling or archiving an integration immediately prevents all of its keys from
authenticating and prevents future webhook fan-out. Persisted deliveries retain
their captured destination and signing configuration and continue normally.
Integration and subscription fan-out eligibility is bounded by the start of
their current enabled interval, so delayed outbox processing cannot replay
events missed while either one was disabled.

Location disablement or archival likewise prevents new location-scoped External
Integration operations and future fan-out without canceling a webhook or
customer-push delivery already materialized under captured configuration.
Location fan-out eligibility is
bounded by the start of its current enabled interval, so re-enabling does not
replay events missed while the location was disabled.

### 4.4 Tenant isolation

All tenant-owned data is protected at both application and database levels. PostgreSQL Row Level Security provides defense in depth. Every transaction accessing tenant-owned rows must establish tenant and location access from a verified account or External Integration credential.

Orders derive tenant ownership through their physical location rather than storing a second direct tenant relationship. RLS policies for orders and their dependent records must verify the location's tenant and, for staff or External Integration operations, the principal's access to that location. A location cannot be reassigned to another tenant after operational data has been created; such a change requires an explicit migration.

Each location stores an IANA time-zone identifier initialized to `UTC`. The current order-numbering increment deliberately uses UTC rather than the stored location setting. The API runtime, injected application clock, and database session use UTC without an environment-selectable alternative in this scope. Location-specific civil-time numbering is deferred.

## 5. Persistence Requirements

The database schema must include tables covering the following concepts. Names and nonessential columns are implementation decisions.

* **Tenants:** stable identity and integration configuration.
* **Locations:** physical restaurant belonging to one tenant, with a required
  trimmed human-readable display name, enabled, disabled, or archived status,
  operational information, and an IANA time-zone identifier fixed at
  `UTC` in this increment. Delete archives the row rather than physically
  removing its stable identity or historical relationships.
* **Accounts:** stable UUID, normalized globally unique email, unique immutable
  ZITADEL provider subject, optional authentication cutoff, tenant ownership,
  tenant role, enabled/disabled/archived status, and audit timestamps. Archived
  email and provider identities remain reserved. Provider subject is required.
* **Browser sessions:** standard Spring Session JDBC identity, creation/access
  timestamps, rolling inactivity expiry, Account principal index and server-only
  provider session attributes. No custom refresh-session table.
* **Account Invitations:** separate authority to create one member account,
  including stable identity, direct tenant ownership, fixed target location and
  manager or operator role, issuing account, SHA-256 hash of a single-use
  32-byte random Base64url bearer credential, `PENDING`, `REDEEMED`, or
  `REVOKED` lifecycle state and internal revocation reason, seven-day absolute
  expiry, and timestamps. Expiration is derived from the deadline rather than
  persisted as a fourth lifecycle state. An invitation contains no account
  recipient email or password and is not itself an account. Multiple pending
  invitations may share a target location and role. Terminal metadata is
  retained indefinitely in the current pre-deployment scope without a
  recoverable bearer secret; a deliberate security-event retention policy
  replaces that default before public deployment.
* **Location assignments:** relationship between a non-admin tenant Account and its
  accessible location, including a manager or operator role. The assignment has
  no independent lifecycle status; effective access requires both its Account
  and Location to be enabled. The current account model permits at most one
  assignment per account; the relationship remains normalized so that this
  cardinality can be changed explicitly in a future migration. Archiving an
  Account retains the assignment as its historical account-to-location
  relationship.
* **Orders:** public tracking identity, owning location, immutable customer-visible text label, current state, and lifecycle timestamps. Tenant ownership is derived through the location.
* **Order history:** order association, resulting state, acceptance time, and the initiator category and identity when known; records are append-only and have an unambiguous order. Initiator categories distinguish users, external integrations, and system actions without coupling history to one authentication mechanism.
* **Automatic order-label allocation:** when a custom label is omitted, the concurrency-safe decimal label is one greater than the count of all orders created for the location during the current UTC date. Custom-labeled orders advance this daily ordinal. No separate numbering date, numeric value, label source, or allocation state is stored on the order; the allocated text is persisted only in its label.
* **External Integrations:** tenant association, normalized name, and enabled, disabled, or archived lifecycle state.
* **API Keys and versions:** immutable scopes, expiration and location grants on
  the stable named key, except for the removal of an archived Location by the
  Location Delete cascade; hashed one-time secret material and overlap validity
  on each version.
* **Webhook subscriptions:** integration association, normalized name, destination, enabled/disabled/archive state, selected locations, and selected event types.
* **Webhook signing-secret versions:** encrypted recoverable signing material, rotation overlap, and retirement state. Encryption keys are externally managed and never generated by the application or image.
* **Order outbox events:** immutable event identity, order/location and tracking association, exact serialized webhook payload, resulting order state, occurrence time, and independent webhook and customer-push fan-out state.
* **Webhook deliveries:** recipient-specific captured destination, payload, signing versions, claim state, one attempt outcome, bounded response details, and durable success or dead-letter state.
* **Customer push subscriptions:** a browser-context capability with hashed
  endpoint, encrypted recoverable endpoint and authentication secret, client
  P-256 public key, VAPID-key fingerprint, expiration when supplied by the
  browser, and last-observed time.
* **Customer push enrollments:** the many-to-many association between a complete
  browser push subscription and active order tracking references.
* **Customer push deliveries:** one mutable retry row per outbox event and
  subscription, including the privacy-minimal payload, freshness deadline,
  claim state, attempt count, next-attempt time, bounded outcome details, and
  accepted, superseded, canceled, expired, or terminal dead-letter state.
* **External order creation identity:** integration-and-location-scoped idempotency value and canonical creation-input fingerprint associated with the created order.

Tenant ownership may be direct or derived through an unambiguous relationship such as order to location to tenant. Tables must carry enough association for RLS enforcement and efficient access checks without duplicating ownership data by default. Passwords and External Integration bearer secrets must never be stored in plaintext. Standard Spring Session JDBC stores opaque session IDs and identity references; protect the database and backups as authentication-bearing data. Provider session bearer tokens are discarded, and provider service credentials remain in private mounted files.

The pre-deployment account model does not grandfather accounts without email.
The consolidated initial migration requires a normalized email for every
account; local development data is recreated manually when applying that
pre-deployment schema change.

Order labels are trimmed, single-line text with a maximum of 32 characters. Automatic labeling is requested by omitting the custom label and uses the order's one-based creation ordinal among all orders at its location during the current UTC date. A provided blank label is invalid. Labels preserve casing, may contain ordinary Unicode text, and are not unique; staff remain responsible for avoiding ambiguous duplicates. Labels cannot be edited after order creation.

## 6. Real-Time Communication

The API exposes a Server-Sent Events stream for each active customer tracking
reference. In the local environment the customer application connects to that
stream on the dedicated API HTTPS origin through NGINX. The stream is
anonymous and read-only: possession of the high-entropy tracking reference
grants access to that order's events but never authorizes a command. The
customer validates each compact event with Zod and treats it only as an
invalidation signal; SWR then retrieves the authoritative customer
representation through REST.

Order-state events are published only after the PostgreSQL transition and history transaction commits. Redis Pub/Sub distributes each event to every live API instance, and each instance forwards it to its locally connected SSE clients for that tracking reference. Publishing and subscription are mandatory application behavior and have no feature flag. Redis remains part of application health reporting and its health contributor must not be disabled. The publishing instance must not turn a committed transition into a failed command response when Redis is unavailable.

Redis Pub/Sub and SSE are intentionally non-durable. The client reconciles through REST when the stream opens or reopens, on focus, and after browser connectivity returns. Cache invalidation may clear the internal SWR entry before refetching, but the customer UI retains the last authoritative order during that request so the active stream is not torn down and reopened. While SSE is disconnected it falls back to approximately 15-second REST polling. No periodic safety request runs while SSE appears healthy, so the current increment accepts the rare possibility that an after-commit Redis publication failure leaves a page stale until another reconciliation trigger.

A terminal transition produces the final invalidation and ends the live stream. Opening an already terminal order returns its REST state without maintaining an SSE connection. Servlet async and error redispatches continue processing the authorization decision made for the original request instead of being treated as new protected commands.

Web Push complements rather than replaces this foreground contract. `READY`,
`COMPLETED`, and `CANCELED` order events are durably fanned out to enrolled
browser subscriptions. The service worker treats a push as a last-known
transition snapshot and notification trigger; opening or focusing the
application still reconciles authoritative state through REST. At-least-once
delivery, unordered push services, and multiple subscriptions require stable
event IDs, state-graph monotonicity, replacement tags, and pre-submission checks
against current PostgreSQL order state. Authenticated staff queue streaming
remains deferred. Future genuinely bidirectional features may introduce
WebSocket independently rather than changing the SSE or Web Push contracts.

## 7. Routing and Deployment

The independently deployable services share one production-like Docker Compose
topology. NGINX is the only normal browser ingress; application and data
services do not publish host ports.

* The customer, staff-panel, and API HTTPS origins and gateway hostnames are
  configured in the root environment file from the values documented in
  `.env.example`.
* Browser-facing REST and SSE requests go directly to the API origin. Spring
  allows credentialed CORS from the customer origin only for customer-owned
  resource families and from the panel origin only for staff-owned resource
  families. The `/external/**` External Integration API and internal management
  endpoints such as Actuator do not receive a browser CORS policy.
* Browser resource families use `/api/{resource-family}/v1`; external resource
  families use `/api/external/{resource-family}/v1`. Location identifiers
  remain in validated bodies or query parameters rather than nested resource
  paths.
* The Next.js services render frontend concerns only; the Spring API owns API
  security, scheduled webhook delivery, and customer-push delivery.
* NGINX, both frontends, and the API share a gateway network. Only the API also
  joins the internal data network containing PostgreSQL and Redis, so neither
  the gateway nor a frontend can reach a data service.
* NGINX selects one of the three applications by exact hostname, rejects
  unknown hosts, keeps frontend and API path spaces separate, and exposes
  Spring Actuator only to container-internal health checks. The API hostname
  forwards only the broad `/api/` namespace; the frontend hostnames reject that
  namespace rather than maintaining a fragile endpoint-by-endpoint allowlist.
* NGINX replaces browser-supplied forwarding metadata with one canonical
  client address, host, HTTPS scheme, and port before proxying. Local traffic
  does not trust forwarding headers. The deployment overlay gives NGINX and
  `cloudflared` a dedicated edge subnet and accepts `CF-Connecting-IP` only
  from that controlled hop. The same core configuration terminates local TLS;
  hosted tunnel traffic uses plain HTTP on the private same-host edge network.
* The shared gateway applies bounded request-body, header, connection, and
  timeout settings. Its tracked-order SSE route disables proxy buffering and
  keeps the upstream read timeout longer than the API's 30-minute emitter
  lifetime.
* Local Compose builds application images from the working tree, mounts
  disposable secrets at `/run/secrets`, and publishes only NGINX HTTPS on
  `127.0.0.1`. Direct service ports are not part of the maintained topology.
* The deployment overlay supports private staging and later production. It
  constructs all three application image references from one registry and one
  immutable release version, keeps infrastructure image versions in the
  repository, adds Cloudflare Tunnel, preserves the same secret paths, applies
  basic CPU and memory limits, and bounds container logs. It publishes no
  service port.
* One standalone repository-owned setup file provides an environment-agnostic
  preparation flow. Interactive use asks before replacing an existing `.env`,
  with replacement from `.env.example` as the default, asks for a secrets
  directory with the Git-ignored repository `secrets/` directory as the
  default, keeps a complete valid application key set unless replacement is
  explicitly accepted, and asks whether to generate a local TLS certificate
  with generation as the default. Explicit options provide the same flow
  non-interactively for hosted preparation. The private-staging invocation
  supplies an absolute external secrets directory and disables local TLS. The
  setup file generates and validates the complete webhook-encryption,
  VAPID, and push-subscription-encryption key set as one unit, installs it with
  a directory mode of `0700` and file modes of `0400`, and fails safely on
  partial, invalid, or existing sets when their handling was not explicit.
  Staging key material is never generated in the checkout or an application
  image.
* The repository versions a non-secret, locally managed Cloudflare Tunnel
  configuration template with three explicit non-wildcard hostname routes to
  NGINX over the private edge network and a final `404` catch-all. A deployed
  copy and its tunnel credential remain external to the repository.
* Each environment file selects either the local or deployment overlay through
  `COMPOSE_FILE`, so ordinary Compose commands do not carry repeated file-list
  arguments.
* PostgreSQL owns the only data volume. Redis Pub/Sub is nondurable and has no
  volume.

GitHub Actions validates pull requests and pushes to `main`. A manual workflow
dispatch for `main` repeats the same validation before publishing. Each
frontend installs its locked dependencies, runs lint and type-checking, runs
its `test` script when one is defined, and creates its production build. The
API runs the complete Maven `verify` lifecycle. Frontend validation and image
construction require the non-secret `NEXT_PUBLIC_API_BASE_URL` and
`NEXT_PUBLIC_CUSTOMER_APP_URL` repository variables and fail clearly when a
required value is absent.

Only a successful manually dispatched `main` workflow may publish application
images. It builds the customer app, panel app, and API runner images for
`linux/amd64` and pushes them to the repository-owned GHCR namespace with the
common immutable `sha-<full-source-revision>` tag. A rerun preserves an
existing service image under that tag and may complete missing images left by
an interrupted publish. The complete workflow must be successful before the
three-image set is eligible for manual deployment. Changing a frontend
build-time repository variable requires a subsequent `main` commit and manual
workflow dispatch rather than overwriting an existing commit-tagged image. The
workflow grants package-write access only to the publishing jobs, does not
publish a moving `latest` tag, does not create GitHub Releases, and does not
deploy any environment.

### 7.1 Current HTTP resource families

The implemented browser-facing contract consists of:

```text
GET    /api/auth/v1/csrf
POST   /api/auth/v1/login
POST   /api/auth/v1/logout
POST   /api/auth/v1/logout-all
POST   /api/auth/v1/password
GET    /api/auth/v1/me
POST   /api/tenant-registrations/v1

GET    /api/locations/v1
GET    /api/accounts/v1
PATCH  /api/accounts/v1/{accountId}/status

GET    /api/account-invitations/v1
POST   /api/account-invitations/v1
DELETE /api/account-invitations/v1/{invitationId}
POST   /api/account-invitation-previews/v1
POST   /api/account-invitation-redemptions/v1

GET    /api/orders/v1
POST   /api/orders/v1
PUT    /api/orders/v1/{orderId}/status

GET    /api/tracked-orders/v1/{trackingReference}
GET    /api/tracked-orders/v1/{trackingReference}/events

GET    /api/customer-notifications/v1/configuration
PUT    /api/customer-notifications/v1/subscription
POST   /api/customer-notifications/v1/subscription-replacement
DELETE /api/customer-notifications/v1/subscription
DELETE /api/customer-notifications/v1/enrollments
```

The accepted Locations and unified administrative-lifecycle increment changes
and extends that browser contract as follows:

```text
GET    /api/locations/v1
POST   /api/locations/v1
PUT    /api/locations/v1/{locationId}
PUT    /api/locations/v1/{locationId}/status
DELETE /api/locations/v1/{locationId}

GET    /api/accounts/v1
PUT    /api/accounts/v1/{accountId}/status
DELETE /api/accounts/v1/{accountId}
```

Authentication returns one current-Account shape with Account UUID, email,
tenant UUID, tenant role, optional location assignment, and capabilities. No
username, Account-kind discriminator, password, or provider credential is
returned. Login/public registration/invitation registration return `200` with
the current Account and an HttpOnly session cookie. Registration accepts
email/password/passwordConfirmation; only member registration requires token.
Anonymous login, local logout and both registration families remain CSRF
protected. Current Account, password changes and global logout require eligible
authentication. Successful password changes and logout return
`204`; generic credentials failure is `401`, input errors `400`, eligibility
or signed-in registration rejection `403`, and identity conflicts `409`.
Tenant Registration Invitation resource families no longer exist.

Location creation accepts only the display name and returns the new enabled
representation with `201`. Rename accepts only the display name. The location
status operation accepts only `ENABLED` or `DISABLED`; archival remains the
Delete operation. Rename and status return the updated representation with
`200`, while Delete returns `204`. Repeating the current normalized name or
status and repeating a successful Delete are idempotent.

Every location mutation requires tenant-administrator authority and locks the
tenant-scoped location before checking or changing it. Cross-tenant targets are
indistinguishable from an unknown target. Rename and status operations also
treat an archived target as unknown, while repeating Delete for an archived
location in the caller's tenant remains idempotently successful. Name
conflicts, attempting to delete an enabled location, and attempting to disable
a location with an active order produce safe `409` responses. The active-order
conflict has a stable problem type so the panel can offer its direct **View
orders** recovery. Each successful disable, enable, or Delete transition and
all of its account, authentication-cutoff, invitation, credential-grant, and subscription
side effects commit atomically; a rejected transition changes nothing.

Account status accepts only `ENABLED` or `DISABLED` and returns the updated
representation. Delete accepts an enabled or disabled manageable member
account, archives it atomically with its authentication and invitation side
effects, and returns `204`. Account lists omit archived accounts, a repeated
Delete for an archived account in the caller's management scope is idempotent,
and status operations treat archived, cross-tenant, and otherwise unmanageable
targets as unknown.

The Account Invitation list, creation, and revocation operations are authenticated and
scoped by the caller's account-management authority. Preview and redemption are
anonymous, accept the bearer token only in their validated request bodies,
retain normal browser CSRF protection, and never log those bodies. Preview
returns only the location name, fixed role, and safe invitation state needed by
the registration page. Redemption removes the token fragment from browser
history after success or a terminal response. Validation failures return `400`,
an available invitation with conflicting email or provider identity returns `409`, a
known expired, revoked, or redeemed invitation returns `410`, an unknown token
returns `404`, and any valid existing panel session receives `403`.

The authenticated administrator management families are
`/api/external-integrations/v1`, `/api/api-keys/v1`,
`/api/api-key-versions/v1`, `/api/webhook-subscriptions/v1`, and
`/api/webhook-signing-secrets/v1`. Their lifecycle operations use the flat
resource-family convention and never accept client-supplied tenant ownership.

The implemented External Integration order contract is:

```text
GET    /api/external/orders/v1
GET    /api/external/orders/v1/{orderId}
POST   /api/external/orders/v1
PUT    /api/external/orders/v1/{orderId}/status
```

Staff order listing accepts an optional `locationId` and active `status`.
External listing accepts an opaque cursor plus optional authorized
`locationId` and `status`. Order creation carries `locationId` and an optional
custom label in its validated body. External creation additionally requires
`Idempotency-Key`. Desired-state updates use idempotent `PUT`; a same-state
request returns the unchanged representation without another history, customer
event, or outbox event.

Docker Compose builds immutable production-mode application images locally and
does not synchronize source files or run Fast Refresh or Spring Boot DevTools.
Both Next.js applications run their standalone build output, and the customer
build generates the Serwist service worker before the runtime image is
assembled. The packaged Spring Boot API runs Flyway migrations and scheduled
webhook and customer-push background jobs in the same application process.
Applying source changes requires rebuilding and recreating the affected
application container.

The customer Next.js application serves the generated service worker with a
root scope, JavaScript content type, restrictive content-security policy, and
explicit no-cache headers so update checks do not reuse a stale script.

The Spring API uses one environment-independent configuration rather than
environment-specific profiles or heuristic staging validation. Hosted
deployments replace the local origins, credentials, identities, and delivery
policies through the root environment file and mount externally managed keys at
the same `/run/secrets` paths used locally. Secure, host-only `SameSite=Lax`
cookie behavior remains a non-configurable application invariant. The complete
environment-variable surface is recorded once in `.env.example`. The shared
deployment overlay mounts environment-specific material at those paths,
constructs the three application image references from one registry plus one
immutable source-revision release value, and takes repository-owned
infrastructure image versions directly from the Compose files. Deployment
remains a manual, operator-initiated procedure: publish images tagged by the
source revision, record their registry digests, pull that release on the VPS,
let API startup apply Flyway migrations, replace dependent services only after
health succeeds, and verify the internal and external paths.

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

## 9. Verification and Acceptance Criteria

* Both frontends build and lint independently.
* Both frontends expose Light, Dark, and System appearance choices, default to
  System, maintain semantic contrast in both resolved appearances, and remain
  usable with reduced motion, increased contrast, keyboard navigation, screen
  readers, and enlarged text at a WCAG 2.2 AA target.
* A locally selected appearance survives a reload in the same browser without
  becoming account data or synchronizing to another browser.
* Both frontends use one coherent visual and interaction system. Staff roles do
  not receive different visual styling, while authorization and capabilities
  continue to control available operations.
* The customer view makes the current order state understandable at a glance,
  with a target recognition time below one second in ordinary usability
  evaluation.
* On the primary tablet layout, staff can create an automatically labelled
  order and reveal its customer QR code through one obvious primary action,
  and can perform a valid order transition through one obvious action.
* Tablet and desktop queues present **In preparation** and **Ready** as adjacent
  lanes; mobile preserves the same model as stacked sections. Queue navigation,
  selection, and actions remain operable with touch, pointer, and keyboard.
* Queue cards expose an approximately minute-granularity elapsed waiting time
  without making an absolute timestamp or continuously animated timer primary.
* Selecting an active-order card opens its customer QR code. The card keeps only
  the inline **Ready** or **Complete** and cancel controls, with no duplicate QR
  button or narrow-width action stack.
* Routine controls, menus, selection rows, and cards in both frontends use the
  same short hover lift, held-press compression, and spring-like release, while
  reduced-motion preferences retain non-spatial feedback without scaling.
* New-order and customer-QR sheets preserve the queue as visible context,
  restore focus to their invoking control when dismissed, and do not lose
  entered custom-label text after a recoverable failure.
* Canceling an order requires an order-specific confirmation and remains
  visually and semantically distinct from its non-destructive alternative.
* The complete staff interface adapts across tablet, desktop, and mobile widths
  without hiding an essential operation; tablet is the primary design target
  and mobile use is secondary.
* Pull-request and `main` CI runs lint, type-check, optional configured frontend
  tests, production frontend builds, and the complete API test suite. A manual
  `main` workflow repeats those checks before it may publish images.
* A successful manually dispatched `main` workflow publishes one `linux/amd64`
  GHCR image for each independently deployable application under the same
  immutable full-source-revision tag and performs no deployment or GitHub
  Release creation.
* Each frontend's handwritten request code and response types match the REST behavior covered by integration tests during the walking vertical slice.
* REST-backed Client Components use keyed SWR state rather than effects for request orchestration, retain cached data during background revalidation, and do not apply order transitions before the Spring API accepts them.
* New orders start in `IN_PREPARATION`, receive an immutable label, and create exactly one initial history entry for that resulting state.
* Automatic labels use the one-based count of all orders at the location for the UTC date under concurrent creation; custom labels are validated, advance that ordinal, and may duplicate existing labels.
* Staff order-list operations return only `IN_PREPARATION` and `READY` orders.
* Backend-mediated ZITADEL password authentication, immediate registration,
  durable/rolling sessions, provider outage/revocation, current local access,
  password change, local/global logout and cookie/CSRF behavior are covered by
  provider-isolated and HTTP contract tests. Live provider acceptance is separate.
* Public registration creates one tenant/administrator and no location. Member
  registration remains single-use and rechecks current invitation eligibility.
* Username, local password hashes, custom refresh sessions, Platform Operator,
  verification staging and Tenant Registration Invitations are absent from the
  initial schema and runtime. Existing local data needs no migration.
* Authorized account listing returns only manageable member accounts in the
  current tenant and, for a manager, only operators assigned to that manager's
  location.
* Location, Account, External Integration, and Webhook Subscription lifecycle
  representations consistently use `ENABLED`, `DISABLED`, and `ARCHIVED`, while
  each feature retains its own status type and unrelated domain state machines
  remain unchanged.
* A tenant administrator can create and rename locations with normalized,
  non-archived tenant-unique display names, disable or enable a location with
  its complete atomic account and invitation cascade, and irreversibly delete a
  disabled location through archival. Managers and operators cannot access
  Location management.
* Location disablement is rejected without side effects while the location has
  an active order. Enabled-location selectors omit disabled and archived
  locations, while the Locations collection shows enabled and disabled
  locations and omits archived ones.
* Orders shows the same no-enabled-location state when no location exists or
  every location is disabled. Its **Create location** action reuses the focused
  Locations modal in place and selects the newly enabled location after
  successful creation.
* Location Enable changes every assigned non-archived member account to
  `ENABLED`, even if it had been disabled independently; archived accounts
  remain archived. Location Delete archives every assigned non-archived account
  and preserves terminal customer tracking and historical attribution.
* An authorized administrator or manager can delete the same manageable member
  Accounts whose statuses they may manage. Delete accepts an enabled or disabled
  account, requires exact-email confirmation, archives it atomically, invalidates
  earlier authentication and revokes pending invitations, retains its historical
  assignment and reserved identity, and removes it from ordinary account lists.
* The Accounts collection contains only created accounts. A trailing icon-only
  **Invitations** control with an accessible tooltip and a positive-count-only
  HeroUI badge opens a responsive focused
  modal containing manageable unredeemed Account Invitations and their actions
  without adding a peer workspace destination. It has complete empty, loading,
  failure, keyboard, focus-restoration, enlarged-text, and mobile states.
* Public sign-in exposes Create account. Email verification and recovery are
  deferred; immutable ZITADEL identity binding and current local authorization
  gate protected work. Password changes are available after login.
* A tenant administrator can access all locations in the tenant but none in another tenant.
* A tenant administrator can issue manager or operator account invitations only
  for locations in its tenant. A location manager can issue only operator
  invitations for its own enabled location, and a location operator cannot issue
  invitations.
* A valid account invitation fixes the tenant, location, and role, and its first
  successful redemption atomically creates one enabled person-oriented member
  account and location assignment without further approval. It never creates or
  reserves an incomplete account before redemption.
* An invitation expires seven days after issuance, stores only a SHA-256 hash
  of its once-revealed 32-byte random bearer token, may be revoked by an
  authorized account manager, and remains unconsumed after invalid or conflicting
  registration input. Multiple invitations for one location and role are
  allowed, but concurrent claims of one invitation create exactly one account.
  Expiration is derived during list, preview, and redemption from `expiresAt`;
  no worker or write-on-read materializes an `EXPIRED` lifecycle state.
* Each pending row identifies the fixed role and location, creator, creation
  time, and absolute expiration time. Its direct icon-only Revoke action
  requires confirmation, and terminal invitations leave the modal while their
  secret-free metadata is retained indefinitely for audit without a history UI
  in the current pre-deployment scope.
* Redemption requires the issuer to remain enabled and authorized. A signed-in
  browser and the API both require explicit sign-out before redemption, while
  verified completion creates the browser session and opens the assigned
  Orders workspace.
* Disabling an issuer atomically revokes its pending invitations with an
  internal reason, while redemption still rechecks current issuer authority.
  The invitation modal uses account-scoped SWR revalidation rather than a live
  staff event stream.
* Invitation links keep the bearer token in the panel URL fragment, submit it
  only in an API request body protected by CSRF, and never expose it through
  gateway URLs, analytics, logs, referrers, or error reports. Known terminal
  states receive specific identity-free guidance while unknown tokens remain
  indistinguishable.
* Invitation redemption creates only a new account and cannot add access to an
  existing account. Required globally unique email remains unverified and is
  not trusted for recovery or external-identity linking.
* A location manager can access and manage orders only in its assigned location.
* A location operator can list, create, read, and update orders only in its assigned location.
* One person-oriented operator account can be used on that staff member's
  multiple panel devices with shared account status and independently rotating
  browser sessions; different staff members use separate accounts.
* Location-scoped API Keys cannot access an unassigned location, and direct order lookup outside a key's grants does not disclose that the order exists.
* Unauthenticated, unauthorized, cross-tenant, and cross-location staff or External Integration access is rejected, including when accessing data directly through repositories protected by RLS.
* Customer tracking works without an account, receives validated SSE invalidations only for the possessed tracking reference, and reconciles through REST after events and reconnects.
* The customer application exposes a valid globally branded manifest and the
  required regular, maskable, Apple touch, and favicon assets supplied for the
  project.
* Android Chrome and iOS Safari can install the customer application and launch
  it in standalone mode without changing the ordinary desktop-browser
  experience.
* Installing from an order page opens that order once on the first installed
  launch while preserving one stable Kairos application identity.
* Each browser or installed-app context independently retains active, explicit
  IndexedDB order snapshots in most-recently-opened order and removes terminal
  orders without allowing a stale response to resurrect them.
* Home renders local active-order summaries without opening SSE, restores the
  last stable Home or active-order destination on subsequent installed launches,
  and tolerates unavailable or invalid local storage.
* The in-app scanner accepts only exact customer-origin order URLs, keeps
  scanning after invalid codes, stops every camera track on exit, supports
  camera switching, retains valid offline scans only in memory for retry, and
  provides device-Camera guidance when browser camera access fails.
* Stopping one tracked order through swipe disclosure or its accessible overflow
  menu removes only that local snapshot and backend enrollment, updates the
  badge, and permits explicit retracking later.
* The generated service worker precaches only the application shell and offline
  route, applies `NetworkOnly` to tracked-order REST, and shows an explicitly
  labeled last-known IndexedDB snapshot when navigation or REST is unavailable.
* Notification permission is requested only from a direct user action; iOS and
  iPadOS receive an Add to Home Screen explanation before any unsupported
  request, and denied permission receives browser-settings guidance.
* Enabling notifications reconciles one complete browser subscription with
  every locally active order, automatically enrolls later active orders,
  replaces browser-rotated subscriptions idempotently, and enforces ten
  subscription contexts per order.
* Disabling notifications removes the complete backend subscription before the
  browser subscription. Stopping order tracking removes its enrollment while
  preserving the app-level preference. Neither flow reports success while
  offline.
* `READY`, `COMPLETED`, and `CANCELED` generate privacy-minimal versioned push
  payloads. The customer service worker validates the payload, deduplicates
  stable event IDs, enforces reachable state transitions, replaces older
  notifications for the same order, updates the active-enrollment badge, and
  opens or focuses the corresponding route.
* Web Push encryption, VAPID headers, public-endpoint enforcement, result
  classification, freshness, jittered retry, `Retry-After`, permanent
  retirement, and retention behavior are covered by protocol-level and
  application-level tests and by current-device acceptance on the required
  browsers before public deployment.
* A valid transition from either the staff panel or an External Integration produces the same persisted state, history record, customer event, and transactional outbox event.
* Integration, API Key, API Key Version, webhook subscription, and signing-secret lifecycle changes preserve one-time secret handling and historical audit attribution.
* Exact idempotent creation replays and same-state status commands create no duplicate order, history, outbox, or customer event.
* Webhook delivery failure cannot roll back or corrupt the committed order transition and is retained as a terminal dead-letter record.
* The REST and webhook contracts remain language-agnostic.

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
* one environment-independent API configuration whose hosted values and
  externally mounted secrets are supplied by the deployment environment;
* one standalone, environment-agnostic setup file with interactive defaults for
  local preparation and explicit non-interactive options for private staging;
  it creates and validates the complete application key set as one unit, uses
  restrictive permissions, and requires explicit existing-key handling;
* a shared, hostname-routing NGINX/application/data Compose topology with small
  local and hosted deployment overlays selected by the environment file,
  controlled forwarding metadata, buffered-disabled SSE, internal-only health
  paths, health-gated dependencies, PostgreSQL-only persistence, nondurable
  staging-authenticated Redis, stable application-secret paths, and a
  documented manual version-pinned deployment sequence with recorded registry
  digests;
* GitHub Actions validation for pull requests and `main`, plus manually
  dispatched `main` validation followed by immutable, commit-tagged
  `linux/amd64` publication of all three application images to GHCR without a
  deployment or GitHub Release.

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

The next External Integration increment publishes an OpenAPI document, rendered
public reference documentation, and formal automated public-contract checks.
The handwritten frontend clients remain in place; generated SDKs are a later,
separate decision.

The ZITADEL session design replaces the transitional Google/Clerk work. Source
and automated provider-isolated checks cover the accepted flow; live acceptance
against the pinned provider and gateway throttling remain required before public
deployment. Direct member creation through `POST /api/accounts/v1` remains
unavailable; managers/operators join only through fixed Account Invitations.

The implemented administrative-lifecycle increment provides
tenant-administrator Location management, removes first-location creation from
tenant registration, adds the reusable zero-enabled-location creation flow in
Orders, standardizes the managed-resource lifecycle vocabulary, adds Account
archival through Delete, and applies the Location cascades and contracts
specified above across the schema, API, panel, and automated verification.

Before any public deployment:

* establish a verified tenant and location database security context and enable
  PostgreSQL Row Level Security for every tenant-owned or ownership-derived
  table;
* introduce a non-bypassable API gateway that preserves a trustworthy client
  address and rate-limits login, password changes, tenant registration, invitation
  redemption, External Integration access, and future recovery or linking
  routes;
* operate and patch ZITADEL, back up its database/master key, rotate backend
  service credentials, and
  provide externally managed webhook-secret encryption keys,
  VAPID signing keys, and push-subscription encryption keys, with documented
  rotation procedures, security-event retention, monitoring, and dependency
  patching;
* complete Android Chrome, iOS/iPadOS Safari, desktop Chrome, and desktop Firefox
  service-worker, offline, subscription, notification, click, badge, and
  subscription-replacement acceptance.

Deferred operational and product work includes live staff queue
synchronization, order archives and search, printable QR artifacts,
tracking-reference expiration, session-management UI, additional
administrators, CAPTCHA, MFA,
passkeys, webhook DLQ
inspection and alerts, automatic webhook retry or redelivery, strict delivery
ordering, application-owned install prompts, and native mobile variants. Any of
these requires an explicitly approved increment and synchronized changes to
this document.
