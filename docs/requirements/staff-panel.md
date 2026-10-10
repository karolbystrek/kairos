# Staff panel requirements

## Contents

- [Staff panel requirements](#staff-panel-requirements)
- [Contents](#contents)
- [3.3 Staff panel](#33-staff-panel)
- [Navigation and onboarding](#navigation-and-onboarding)
- [Orders and customer QR](#orders-and-customer-qr)
- [Management collections and details](#management-collections-and-details)
- [Lifecycle vocabulary and confirmations](#lifecycle-vocabulary-and-confirmations)

### 3.3 Staff panel

The staff panel must:

* offer public email/password registration that creates a tenant and its first
  administrator immediately, then requires first-location creation in the
  dashboard before the administrator can use the workspace, without email verification;
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

#### Navigation and onboarding

The panel application provides a public restaurant-owner landing page at
`/`. It briefly explains QR-based order tracking and queue management
without physical pagers or a customer app download. Its primary action links to
public registration at `/registration`, while Sign in links to `/login` and the
staff workspace lives at `/dashboard`. Signed-in visitors to `/` or `/login`
are redirected to `/dashboard`; signed-out dashboard visitors go to `/login`
after the session check. Authentication failures leave the public landing page
usable and keep the existing recovery state on staff routes. The creator's email
contact appears as secondary footer information; contacting the creator or
receiving an invitation is not required to register. The page uses spacious typography and the shared
responsive, accessible light/dark design language.

The staff experience keeps the active queue visually primary. Administrative
capabilities must not compete with frequent order creation, QR presentation,
or valid order transitions merely because they are available to the signed-in
account.

Orders, Locations, Accounts, and Integrations use one adaptive tab-style
navigation system: a centered rounded segmented control on tablet and desktop and bottom
navigation on narrow mobile layouts. Only authorized destinations are present.
An administrator with no non-archived location first sees the required
first-location modal over the rendered workspace. The workspace remains visible
through the shared blurred backdrop but cannot be interacted with while the
required modal is open. The panel waits for
account-scoped authoritative location data before showing either onboarding or
the workspace; a failed initial request shows Retry and does not permit skipping
onboarding. A disabled location still counts as existing.
The shell and visual treatment do not vary by role. Appearance and Account
remain trailing icon-only utilities on the same level rather than peer workspace
destinations. Account opens a menu containing Change password, Sign out, and
Sign out everywhere. Ordinary Sign out requires a focused confirmation for the
current device. Sign out everywhere requires its own destructive confirmation
explaining that every device, including the current one, will be signed out;
Cancel sends no request. Failed logout retains the confirmation for retry, and
pending logout prevents repeat submissions without preventing popup dismissal. Change password uses
the existing focused form, omits persistent password-length guidance, and retains
validation errors and the explanation that success signs out every device.

Locations appears between Orders and Accounts for a tenant administrator and
is absent for managers and operators. The Locations collection includes enabled
and disabled locations and omits archived locations. Operational selectors and
new Account Invitation, API Key, and webhook-subscription forms offer only
enabled locations.

#### Orders and customer QR

The active queue uses adjacent **In preparation** and **Ready** sections on
tablet and desktop and the same two sections stacked vertically on mobile.
These sections use headings, counts, whitespace, and a shared separator rather
than large rounded lane containers. Order creation begins from one compact,
icon-and-text creation action at the start of the active workspace. Its focused
sheet contains one optional label field whose
placeholder communicates the automatic default; leaving it empty requests an
automatic label and entering text requests a custom label, without a separate
mode selector. Successful creation continues directly into a large,
dismissible customer QR sheet; explicitly requesting an existing order's QR
uses the same presentation. The underlying queue remains visible to preserve
context. The Orders, Locations, Accounts, and Integrations creation actions
share one 44-pixel control height, an icon with Polish text, an accessible name
and a pointer tooltip above the workspace content on every layout. Repeated Orders, Locations, Accounts and
Integrations titles are visually hidden; the selected navigation tab identifies
the workspace, while an accessible heading remains for assistive technology. The queue-location selector
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

#### Management collections and details

Locations, Accounts, and External Integrations initially show only responsive
card collections and relevant creation controls, with no automatic selection or
persistent right-hand details. Selecting a card opens details and applicable
management actions in the shared centered popup, adapted to available mobile
space. There is no collection search or filtering in this increment.

New member access begins in an invitation popup capturing recipient email,
fixed location, and manager or operator role. The resulting once-revealed link
is copied and shared manually. Accounts includes non-archived created accounts
and manageable pending invitations in one collection. Both card kinds show
email, role, and location; location and integration cards show name. Card colour
alone visually communicates enabled, disabled, or pending status. Accessible
card labels expose status, and the detail popup states it explicitly. Existing
shared geometry, touch targets, focus and press feedback remain consistent.

Selecting an invitation opens its recipient, role, location, issuer, creation
and expiration times, and confirmed Revoke action. Selecting an existing account
opens its details and permitted status and Delete actions. Disable retains its
hold confirmation; Delete uses hold confirmation. Both enabled and disabled
manageable accounts can be deleted.

Integration details retain the compact segmented control between API Keys and
Webhooks and their one-time secret handling. Detail headers share the
resource-type eyebrow, title or inline editor, and stable trailing action rail.
Integration identity becomes editable only through Edit; direct confirm/cancel
controls replace the title in edit mode. Contextual labels omit a repeated
entity name when the popup makes the target unambiguous. The new-integration
popup uses the direct **Integration name** placeholder with an accessible label.

Location cards retain enabled-first display-name ordering with stable identity
as a tie-breaker. The popup action rail contains Edit, Disable or Enable, and
Delete. Delete remains visible but unavailable until the location is disabled
and another non-archived location exists. Details explain that the last location
can be disabled but cannot be deleted, and offer no time-zone editor or unrelated
operational fields.

If confirmed location disablement is rejected because the location still has
active orders, the location remains enabled and the panel explains that those
orders must be completed or canceled first. A direct **View orders** action
opens Orders filtered to that location.

Accounts and Integrations remain available when all existing locations are
disabled. Account Invitation creation is unavailable with quiet inline guidance
to enable a location and an explanatory creation-control tooltip, without an
"Account invitations unavailable" warning container. External Integrations may
still be created, while API Key and webhook-subscription creation remains
unavailable with inline guidance until an enabled location exists.

Integration credential and webhook forms use neutral, task-specific
placeholders rather than suggesting a restaurant, point-of-sale product, or
other example identity.

Repeated administrative actions use one shared visual vocabulary across the
panel: Edit uses the pencil, Disable uses the prohibited-state symbol, Enable
uses the check, and Delete uses the trash symbol. Their icon-and-text
controls share size, corner treatment and accessible naming. Consequential
controls include explicit Polish labels and nearby consequence text.

#### Lifecycle vocabulary and confirmations

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

Administrative actions that remove access, stop delivery, invalidate a credential,
retire an active secret version, or delete a resource require deliberate hold
confirmation before the request is sent. This includes disabling Locations,
Accounts, Integrations and Webhook Subscriptions; revoking Account Invitations
and API Keys; retiring overlapping webhook secrets; and deleting managed
resources. The action stays within the existing detail popup or inline section,
with consequence text identifying the affected resource. It never opens a second
confirmation popup. Order cancellation and logout retain their existing focused
confirmation flows.

Hold controls use Polish text such as **Przytrzymaj, aby usunąć** and require
1.6 seconds of uninterrupted pointer, touch, Space or Enter input. The progress
fill is linear; release, moving outside, losing focus, cancellation, disabling
or unmounting prevents submission. Reduced motion replaces the moving fill with
static feedback without shortening the confirmation period. Assistive virtual
activation, which supplies no hold events, requires two explicit activations;
the second is labelled **Potwierdź operację**. Pending requests cannot repeat,
failed requests retain error feedback and allow retry, and there is no undo.
This replaces typed location-name, integration-name and account-email checks.

Re-enabling remains an ordinary click. Location Enable explains beside its
control that every assigned non-archived member account becomes `ENABLED`,
including accounts disabled independently. Location Delete is available only
while disabled and never for the last non-archived location; it explains that
the location and assigned accounts cannot be restored while historical orders
remain readable. Account Delete signs the account out, removes it from ordinary
Accounts, revokes pending invitations and permanently removes access. Success
clears the archived selection and revalidates account and invitation state.
Integration Disable explains the immediate credential and webhook effect;
Delete irreversibly archives the integration internally.
