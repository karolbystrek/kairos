# Order lifecycle and customer application

## Functional requirements

### Order lifecycle

An order follows a controlled lifecycle:

1. **In preparation:** staff or an authorized External Integration creates an accepted order for an accessible location. The API immediately places it in preparation and returns its daily number, optional label and items, and QR-code tracking reference.

Every order receives a stable, non-editable number unique per location and local business day. Numbering starts at 1 after each midnight in the location's time zone, has no upper limit and never duplicates under concurrent creation; labels, items and cancellations do not affect it. The label is optional single-line text of up to 80 characters that need not be unique; blank input means no label. Neither number, label nor items can change after creation.
2. **Ready for pickup:** staff or an External Integration marks the order ready and connected customer pages refresh their visible state.
3. **Completed:** the order is collected, the live session ends, and later scans show a terminal state.
4. **Canceled:** the order is canceled before completion, connected customer pages refresh, and the live session ends.

There is no separate `CREATED` state: every created order is automatically accepted into `IN_PREPARATION`. From there it may become `READY` or `CANCELED`; a ready order may become `COMPLETED` or `CANCELED`. An active order whose status has not changed for 24 hours (`kairos.retention.order-expiry`) becomes `EXPIRED`, set only by the hourly retention worker as a `SYSTEM` transition: staff and External Integrations cannot choose it. Completed, canceled and expired orders are terminal. Expiry emits `order.expired` to webhooks and live streams, but sends no customer push, schedules no review invitation and records no analytics input. The customer cannot request transitions.

The backend validates every transition regardless of whether it originates from the panel or an External Integration.

### Customer application

The customer application must:

* open directly from an order-specific QR code without login or installation;
* display the current order state before establishing real-time communication;
* open an anonymous Server-Sent Events stream scoped only to the referenced order;
* validate and apply customer state carried by each event without a REST request, rejecting status regressions across REST, SSE, Web Push and stored snapshots;
* rely on native `EventSource` reconnection and receive the current state directly on every stream opening or reopening and after Redis subscription recovery;
* poll REST approximately every 15 seconds while the event stream is disconnected, and stop fallback polling when it reconnects;
* reconcile through REST on focus and browser connectivity restoration;
* make an active order's current status the dominant content, its number the
  primary identifier and its label, when present, a secondary line, express the
  current state once without a duplicate status label, show a status timestamp only when the displayed data
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

#### Installation and application identity

The customer application is installable as one globally branded **Kairos** web
application while remaining fully usable without installation. Installation is
browser- or operating-system-initiated; Kairos does not show its own install
prompt in the current scope. The installed application uses standalone display
mode, the shared appearance preference, and no
orientation lock. Android Chrome and iOS
Safari are the required installation targets, while desktop browsers must
continue to provide the ordinary web experience.

The manifest uses `Kairos Order Tracking` as its full name and `Kairos` as its
short name. Its stable application ID and scope cover the complete customer
origin. The scanner default page uses the ordinary root start URL. An order page
exposes an order-aware manifest with the same stable identity and a start URL that enters
through the root route with only the tracking reference required for bootstrap.
The manifest asks supporting browsers to reuse an open installed window for
in-scope links (`launch_handler` `navigate-existing`). iOS and iPadOS never open
the Home Screen app from links, so a QR code scanned with the Camera app always
opens Safari; Android opens it in the app only when installed as a full Chrome
app and the camera hands the link to the system. On Apple mobile devices, the
installed scanner default page and the installation guide therefore tell
customers to scan later orders inside Kairos.

An installation initiated from an order page opens that order on the first
installed launch while retaining one stable Kairos application identity and
origin-wide scope. Each browser or installed-app context subsequently retains
its own current-order destination and local tracking snapshots. The contexts
are not synchronized with one another, with another device, or through the
backend. Local tracking does not establish customer identity or order ownership.

#### Current order and scanner default page

The default page shows one **Scan QR code** action alongside the Notifications
and Appearance utilities. There is no order collection, history, Home control,
swipe disclosure, or stop-tracking action.

Opening an order records it as the current destination before REST succeeds.
The root route restores that unclosed order in ordinary browser and installed
contexts, including after it becomes terminal. Completion or cancellation does
not navigate away: the terminal view stays visible until the customer selects
**Close order**, which clears the current destination and returns to the scanner
default page. Active and loading views have no close or default-page action.
An invalid or missing order provides **Back to scanner** recovery; connectivity
failures preserve the current destination. Browser controls remain free to leave
the site or close it. Destination persistence is best effort when IndexedDB is
unavailable or inaccessible.

IndexedDB retains distinct active `IN_PREPARATION` or `READY` snapshots for
explicit offline display and notification enrollment, without exposing a local
collection. Reopening an order refreshes its stored number, label, status, and
server-provided `updatedAt`. A terminal REST response or accepted push removes
its active snapshot, while a short-lived terminal tombstone prevents an older
response or push from resurrecting it. This cleanup does not clear the current
destination. Closing a terminal view preserves app-level notification consent
and eligibility for any pending final notification.

#### Scanner and offline behavior

The default page opens a dedicated scanner view only after a direct customer
action. The scanner prefers the rear camera, allows switching when multiple cameras are
available, decodes locally, and accepts only customer-origin URLs whose exact
path is `/orders/<UUID>`. Invalid codes leave scanning active with an inline
error. Every camera track stops after success, cancellation, navigation, or
unmount. Camera failure directs the customer to the device Camera app. A valid
code scanned offline remains only in memory and can be retried after connectivity
returns; no tracking snapshot is stored until the order loads successfully.

Order REST endpoints are network-only in the service worker. The application
shell and a dedicated offline route are precached, while application code
explicitly reads last-known order snapshots from IndexedDB after a network
failure. An offline view must label its data and timestamp as last known and
must not imply that it is current. When no snapshot exists, the application
shows an offline explanation rather than manufacturing an order state.

#### Notification consent and subscriptions

Notification consent belongs to the complete Kairos PWA context rather than an
individual order. Kairos requests browser permission only from a direct user
action. On iOS and iPadOS outside standalone Home Screen mode, the control first
explains how to add Kairos to the Home Screen instead of calling the permission
API. A denied permission shows browser-settings guidance. Once enabled, every
locally active order is automatically reconciled as an enrollment for the
current browser push subscription. New active orders are enrolled silently, and
terminal orders are removed after their final notification delivery has been
materialized. The API limits a subscription to ten contexts per order.

Three seconds after the first order loads successfully, the application shows a
dismissible notification popup when permission can be requested, or
an illustrated installation guide on Apple mobile devices requiring Home Screen
installation. Navigation away cancels the delay. Enabled, granted, blocked, and
unsupported browser states skip the automatic permission invitation. Dismissal
is remembered locally, with an in-memory fallback when storage is inaccessible.
Enable notifications starts the popup exit animation and requests permission in
the same tap handler, without waiting for animation completion. The Apple guide
uses three steps: Share in Safari, Add to Home Screen, then open Kairos and tap
the bell. The bell reopens required installation instructions after dismissal;
other contexts enable directly or show inline blocked/unsupported guidance.
The bell immediately reflects the requested state after permission is granted,
with a pending label until subscription persistence finishes. Disabling receives
the same immediate feedback. Pending actions prevent duplicate taps, while
failure restores the confirmed state and shows inline error guidance.
Tracking remains usable without installation or notifications.

The application exposes one persistent notification control in every customer
view. Its icon distinguishes enabled notifications
from all off or unavailable states: a normal bell offers notification enabling,
while a crossed bell indicates that notifications are currently enabled and
the action will disable them. Disabling notifications durably retires the current
subscription and all of its enrollments before removing the browser
subscription. Disabling notifications requires a network connection so the UI
does not make a false backend-cleanup promise.
Browser-initiated subscription replacement is reconciled once from the service
worker and idempotently retried on the next application start if needed.

Every transient in-app notification message provides a Dismiss action that
clears the current message without changing notification settings.

#### Push payloads and application badges

Order-status notifications are generated for `READY`, `COMPLETED`, and `CANCELED`
transitions only. Their title is the global Kairos brand, their body describes
the state without disclosing the order label, and their click target is the
order route. Notifications use one replacement tag per order. The service worker
validates a versioned payload, deduplicates its stable event ID, and applies the
transition only when it is reachable from the locally stored state graph.
REST or SSE reaching the same state does not consume that notification. Terminal
snapshot cleanup preserves eligibility for the final notification during the
push freshness window, including after Close order; notification opt-out
suppresses pending notifications.
Malformed or unprocessable payloads produce a generic, privacy-preserving
notification. The foreground tracking page continues to use REST and SSE and
may issue one short vibration pulse for a newly observed transition when the
browser permits it; no custom notification vibration pattern or sound is used.

The application badge, where supported, is the number of locally active orders
enrolled for notifications. Kairos does not add Background Sync, Periodic
Background Sync, Screen Wake Lock, synthetic audio, or a separate background
polling promise. The service worker does not force `skipWaiting` or immediately
claim existing clients; an update activates through the browser lifecycle.

#### Google review invitations

Locations may enable one initial Google review invitation per completed order
in each anonymous browser context. Canceled orders never qualify. The API
captures the due time at completion: `REVIEW_INVITATION_DELAY` defaults to `30m`,
and local Compose defaults to `1m`. Changes affect future completions; no repeat
reminders are scheduled.

The invitation is never sent as a push notification. A closable in-app prompt
appears when a customer returns after the delay or keeps the app open until it
elapses. The prompt uses neutral wording
and offers Review on Google and Dismiss; opening Google or dismissal consumes
the invitation in that browser context. No satisfaction filtering, incentives,
positive-rating requests are included. Best-effort review-link clicks and explicit
dismissals follow [usage analytics](usage-analytics.md). See
[Google's contribution policy](https://support.google.com/business/answer/7400114).

Active snapshots, live streams and ordinary enrollments still end normally.
A separate IndexedDB record preserves only the tracking reference and consumed
state; REST revalidates invitation eligibility and the Google link before
presentation. Offline recovery does not present cached review eligibility.
Removing/changing the review link or disabling/archiving the location suppresses
outstanding invitations permanently, including after re-enablement.
Browser-storage clearing loses local follow-up and dismissal state; separate
devices/contexts are not a shared customer identity.

The invitation stays in-app so that it is not an electronically sent commercial
message requiring prior consent; see [legal readiness](legal.md#google-review-invitations).
