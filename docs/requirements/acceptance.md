# Verification and acceptance criteria

## Contents

- [Verification and acceptance criteria](#verification-and-acceptance-criteria)
- [Contents](#contents)
- [9. Verification and Acceptance Criteria](#9-verification-and-acceptance-criteria)
- [Build and REST consistency](#build-and-rest-consistency)
- [Authentication, accounts and locations](#authentication-accounts-and-locations)
- [Customer tracking, PWA and Web Push](#customer-tracking-pwa-and-web-push)
- [Integration and delivery invariants](#integration-and-delivery-invariants)

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
* Queues present **In preparation** and **Ready** sections on every layout.
  Queue navigation, selection, and actions remain operable with touch, pointer,
  and keyboard.
* Queue cards expose an approximately minute-granularity elapsed waiting time
  without making an absolute timestamp or continuously animated timer primary.
* Selecting an active-order card opens its details with the customer QR code.
  The card keeps only the inline **Ready** or **Complete** and cancel controls.
* New-order and order-details popups preserve the queue as visible context,
  restore focus to their invoking control when dismissed, and do not lose
  entered label text after a recoverable failure.
* Canceling an order requires an order-specific confirmation and remains
  visually and semantically distinct from its non-destructive alternative.
* The complete staff interface adapts across tablet, desktop, and mobile widths
  without hiding an essential operation; tablet is the primary design target
  and mobile use is secondary.
### Build and REST consistency

* Pull-request and `main` CI runs lint, type-check, optional configured frontend
  tests, production frontend builds, and the complete API test suite. A manual
  `main` workflow repeats those checks before it may publish images.
* Each frontend's handwritten request code and response types match the REST behavior covered by integration tests during the walking vertical slice.
* REST-backed Client Components use keyed SWR state rather than effects for request orchestration, retain cached data during background revalidation, and do not apply order transitions before the Spring API accepts them.
* New orders start in `IN_PREPARATION`, receive an immutable number, optional label and items, and create exactly one initial history entry for that resulting state.
* Numbers are unique per location and local business day under concurrent creation, restart at 1 after the location's local midnight and are independent of labels; labels are validated, optional and may duplicate existing labels.
* Menu items require the location's current menu, reject stale or repeated items and locations without a menu, and stay unchanged after menu edits; changing items in the panel regenerates the editable label.
* Each location owns an independent menu; only administrators or the location's manager create, edit or delete it, and copying another location's items leaves the two menus independent.
* Staff order-list operations return only `IN_PREPARATION` and `READY` orders.
### Authentication, accounts and locations

* API-owned password authentication, email-verified registration,
  durable/rolling sessions, revocation, current local access, password change,
  logout and cookie/CSRF behavior are covered by integration and HTTP contract
  tests against the real credential store.
* Public registration rejects invalid email, a short password, and mismatched
  confirmation with errors attached to the correct fields before creating an
  identity. Identity conflicts have an Email field error.
* Public registration creates no tenant or administrator before a correct
  6-digit email code. Resending observes a 60-second cooldown, incorrect codes
  permit five attempts and the one-hour code expiry is enforced. Unverified sign-in
  resumes the code step; abandoned identities without Accounts can be replaced.
* Successful public email confirmation creates the tenant, administrator and browser
  session without creating a location. The main panel requires first-location
  creation, survives reload and later sign-in with onboarding incomplete,
  prevents dismissal, retains failed input, and opens the new location's Orders
  only after creation succeeds. A saved disabled location does not repeat onboarding.
* Member registration remains single-use and rechecks current invitation eligibility.
* Deleting the last non-archived location is rejected without archival cascades,
  including concurrent deletion attempts. Disabling the final location remains
  available under the ordinary active-order restriction. Accounts and Integrations
  remain behind required onboarding until a location exists, and are available
  when all locations are disabled without the invitation warning container.
* Username, local password hashes, custom refresh sessions, Platform Operator,
  Tenant Registration Invitations are absent from the
  initial schema and runtime.
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
  account, requires inline hold confirmation, archives it atomically, invalidates
  earlier authentication and revokes pending invitations, retains its historical
  assignment and reserved identity, and removes it from ordinary account lists.
* Accounts, Locations, and Integrations initially show card collections without
  automatic selection. Selecting a card opens its details and management
  actions. Cards and details expose status with accessible labels; no search or
  filters are included.
* Accounts includes pending invitations alongside created accounts. Both show
  email, role, and location; Locations and Integrations show name. Invitation
  details show role, location and expiry and retain confirmed revocation.
* Public sign-in exposes Create account. Email verification and recovery are
  deferred; immutable identity binding and current local authorization
  gate protected work. Password changes are available after login.
* A tenant administrator can access all locations in the tenant but none in another tenant.
* A tenant administrator can issue manager or operator account invitations only
  for locations in its tenant. A location manager can issue only operator
  invitations for its own enabled location, and a location operator cannot issue
  invitations.
* A valid account invitation fixes the recipient email, tenant, location, and
  role, and its first successful redemption atomically creates one enabled person-oriented member
  account and location assignment without further approval. It never creates or
  reserves an incomplete account before redemption.
* An invitation expires seven days after issuance, stores only a SHA-256 hash
  of its emailed 32-byte random bearer token, may be revoked by an
  authorized account manager, and remains unconsumed after invalid or conflicting
  registration input. Multiple invitations for one location and role are
  allowed for different emails, but active invitations are unique per tenant/email.
  Confirmed creation/resend replaces the old entry and token atomically, requires
  authority over both targets, and starts a fresh seven-day lifetime. The panel
  and API expose no shareable token or link. Concurrent claims of one invitation create exactly one account.
  Expiration is derived during list, preview, and redemption from `expiresAt`;
  no worker or write-on-read materializes an `EXPIRED` lifecycle state.
* Each pending invitation fixes a required valid normalized recipient email.
  Creation, list, and preview expose it; invited registration displays it
  read-only with its location and role. Mismatched redemption
  email is rejected before creating an identity and rechecked atomically.
  Details identify the fixed role and location and the expiry. Its Revoke action
  requires inline hold confirmation. The API history retains terminal metadata,
  lifecycle state and queued/sent/failed email delivery state; the panel folds
  them into one status (Pending, Not sent, Expired) and omits redeemed entries,
  which their account represents. Managers
  see only operator invitations for their location; administrators see the
  tenant. Active pending invitations with failed delivery can be resent after
  confirmation; expired invitations use ordinary creation.
* Redemption requires the issuer to remain enabled and authorized. A signed-in
  browser and the API both require explicit sign-out before redemption, while
  verified completion creates the browser session and opens the assigned
  Orders workspace.
* Disabling an issuer atomically revokes its pending invitations with an
  internal reason, while redemption still rechecks current issuer authority.
  The invitation collection uses account-scoped SWR revalidation rather than a live
  staff event stream.
* Invitation links keep the bearer token in the panel URL fragment, submit it
  only in an API request body protected by CSRF, and never expose it through
  gateway URLs, analytics, logs, referrers, or error reports. Known terminal
  states receive specific identity-free guidance while unknown tokens remain
  indistinguishable.
* Invitation redemption creates only a new account and cannot add access to an
  existing account. The fixed globally unique invited email is marked verified without a code
  step; redemption cannot change it or link an existing identity.
* A location manager can access and manage orders only in its assigned location.
* A location operator can list, create, read, and update orders only in its assigned location.
* One person-oriented operator account can be used on that staff member's
  multiple panel devices with shared account status and independently rotating
  browser sessions; different staff members use separate accounts.
* Location-scoped API Keys cannot access an unassigned location, and direct order lookup outside a key's grants does not disclose that the order exists.
* Unauthenticated, unauthorized, cross-tenant, and cross-location staff or External Integration access is rejected, including when accessing data directly through repositories protected by RLS.
### Customer tracking, PWA and Web Push

* Customer tracking works without an account, receives validated customer state
  only for the possessed tracking reference on stream connect, changes and Redis
  recovery, applies it without REST fetches, and never regresses under unordered
  REST, SSE or Web Push arrivals. Disconnected tracking retains REST fallback.
* Staff queues stream authorized active snapshots for location and tenant scopes,
  include other staff/integration changes, and recheck access within five minutes.
* Committed order changes wake push/webhook fanout and delivery immediately;
  rollback wakes nothing, and five-second recovery preserves multi-instance
  claims, abandoned work and due retries.
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
* The default page offers one Scan QR code action with Notifications
  and Appearance, without an order collection or history. The root route
  restores the current unclosed order in browser and installed contexts,
  including terminal orders, and tolerates unavailable or invalid local storage.
* Active and loading orders expose no Home or close action. Completed and
  canceled orders remain visible until Close order returns to the default page;
  invalid or missing orders provide scanner recovery. Network errors preserve
  the current destination.
* The in-app scanner accepts only exact customer-origin order URLs, keeps
  scanning after invalid codes, stops every camera track on exit, supports
  camera switching, retains valid offline scans only in memory for retry, and
  provides device-Camera guidance when browser camera access fails.
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
  browser subscription and does not report success while offline. Closing a
  terminal order preserves the app-level preference and any pending final
  notification.
* `READY`, `COMPLETED`, and `CANCELED` generate privacy-minimal versioned push
  payloads. The customer service worker validates the payload, deduplicates
  stable event IDs, enforces reachable state transitions, replaces older
  notifications for the same order, updates the active-enrollment badge, and
  opens or focuses the corresponding route.
* Web Push encryption, VAPID headers, public-endpoint enforcement, result
  classification, freshness, jittered retry, `Retry-After`, permanent
  retirement, and retention behavior are covered by protocol-level and
  application-level tests and by current-device acceptance on the required
  browsers.
### Integration and delivery invariants

* A valid transition from either the staff panel or an External Integration produces the same persisted state, history record, customer event, and transactional outbox event.
* Integration, API Key, webhook subscription, and signing-secret lifecycle changes preserve one-time secret handling and historical audit attribution.
* Exact idempotent creation replays and same-state status commands create no duplicate order, history, outbox, customer event or analytics measurement.
* Focused PostgreSQL checks verify anonymous analytics rollback/retry, concurrent processing, duration/hour/bucket calculations, collection without backfill, tenant isolation, location-purge survival and permanent tenant-deletion races; see [analytics invariants](order-analytics.md).
* Focused usage checks verify predefined actions, atomic invalid-batch rejection, body/rate limits, eligible review attribution, tenant/location isolation, concurrent increments/deletion and representative panel/review collection; see [usage counts](usage-analytics.md).
* Focused reporting checks verify role and RLS scope, manual platform grant/revocation, merged means and empty samples, DST and fractional-offset day assignment, range bounds, purged-location identity, tenant-deletion removal and sanitized health; see [analytics reporting](analytics-reporting.md).
* Webhook delivery failure cannot roll back or corrupt the committed order transition and is retained as a terminal dead-letter record.
* The REST and webhook contracts remain language-agnostic.
