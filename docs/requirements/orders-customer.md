# Order lifecycle and customer application

## Contents

- [Order lifecycle and customer application](#order-lifecycle-and-customer-application)
- [Contents](#contents)
- [3. Functional Requirements](#3-functional-requirements)
- [3.1 Order lifecycle](#31-order-lifecycle)
- [3.2 Customer application](#32-customer-application)
- [Installation and application identity](#installation-and-application-identity)
- [Current order and scanner default page](#current-order-and-scanner-default-page)
- [Scanner and offline behavior](#scanner-and-offline-behavior)
- [Notification consent and subscriptions](#notification-consent-and-subscriptions)
- [Push payloads and application badges](#push-payloads-and-application-badges)

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

#### Installation and application identity

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
origin. The scanner default page uses the ordinary root start URL. An order page
exposes an order-aware manifest with the same stable identity and a start URL that enters
through the root route with only the tracking reference required for bootstrap.

An installation initiated from an order page opens that order on the first
installed launch while retaining one stable Kairos application identity and
origin-wide scope. Each browser or installed-app context subsequently retains
its own current-order destination and local tracking snapshots. The contexts
are not synchronized with one another, with another device, or through the
backend. Local tracking does not establish customer identity or order ownership.

#### Current order and scanner default page

The default page shows one horizontally and vertically centered **Scan QR code**
action, with Notifications and Appearance in the trailing utility group. Its
label and icon scale together, with the large label above an oversized QR icon.
The control has no resting container and reveals its button surface on hover,
keyboard focus, or press. There is no order collection, history, Home control,
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
collection. Reopening an order refreshes its stored label, status, and
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

The first successfully loaded order temporarily expands the notification bell
into a dismissible “Notify me” button when browser permission can be requested
directly. The order screen remains visible and tapping the bell requests permission immediately.
Only Apple mobile contexts requiring Home Screen installation show a popup,
with three icon-led steps: Share in Safari, Add to Home Screen, and open Kairos
then tap the bell. This includes desktop-mode iPads. Dismissal of either guidance
is remembered locally, with an in-memory fallback when storage is inaccessible.
The bell reopens installation instructions regardless of prior dismissal; all
other contexts enable directly or show inline blocked/unsupported guidance.
Tracking remains usable without installation or notifications.

The application exposes one persistent notification control in the top-right
corner of every customer view. Its icon distinguishes enabled notifications
from all off or unavailable states: a normal bell offers notification enabling,
while a crossed bell indicates that notifications are currently enabled and
the action will disable them. Disabling notifications durably retires the current
subscription and all of its enrollments before removing the browser
subscription. Disabling notifications requires a network connection so the UI
does not make a false backend-cleanup promise.
Browser-initiated subscription replacement is reconciled once from the service
worker and idempotently retried on the next application start if needed.

Transient in-app notification guidance aligns to the customer content width and
uses the same flat secondary surface and corner scale as the rest of the
interface. Every such message provides a trailing, icon-only Dismiss action
that clears the current message without changing notification settings.

#### Push payloads and application badges

Background notifications are generated for `READY`, `COMPLETED`, and `CANCELED`
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
