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
### Build and REST consistency

* Pull-request and `main` CI runs lint, type-check, optional configured frontend
  tests, production frontend builds, and the complete API test suite. A manual
  `main` workflow repeats those checks before it may publish images.
* Each frontend's handwritten request code and response types match the REST behavior covered by integration tests during the walking vertical slice.
* REST-backed Client Components use keyed SWR state rather than effects for request orchestration, retain cached data during background revalidation, and do not apply order transitions before the Spring API accepts them.
* New orders start in `IN_PREPARATION`, receive an immutable label, and create exactly one initial history entry for that resulting state.
* Automatic labels use the one-based count of all orders at the location for the UTC date under concurrent creation; custom labels are validated, advance that ordinal, and may duplicate existing labels.
* Staff order-list operations return only `IN_PREPARATION` and `READY` orders.
### Authentication, accounts and locations

* Backend-mediated ZITADEL password authentication, immediate registration,
  durable/rolling sessions, provider outage/revocation, current local access,
  password change, local/global logout and cookie/CSRF behavior are covered by
  provider-isolated and HTTP contract tests. Live provider acceptance is separate.
* Public registration rejects invalid email, a short password, and mismatched
  confirmation with errors attached to the correct fields before provider
  provisioning. Local/provider identity conflicts have an Email field error;
  known provider password failures have a safe Password field error.
* Successful public registration creates the tenant, administrator and browser
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
  in the current development scope.
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
### Customer tracking, PWA and Web Push

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
  browsers.
### Integration and delivery invariants

* A valid transition from either the staff panel or an External Integration produces the same persisted state, history record, customer event, and transactional outbox event.
* Integration, API Key, API Key Version, webhook subscription, and signing-secret lifecycle changes preserve one-time secret handling and historical audit attribution.
* Exact idempotent creation replays and same-state status commands create no duplicate order, history, outbox, or customer event.
* Webhook delivery failure cannot roll back or corrupt the committed order transition and is retained as a terminal dead-letter record.
* The REST and webhook contracts remain language-agnostic.
