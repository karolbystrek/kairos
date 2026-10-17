# Staff panel requirements

### Staff panel

The dashboard keeps its selected workspace in the URL fragment (for example,
`#integrations`). Refresh, direct links and browser Back/Forward retain that
selection. Missing, unknown, unavailable or onboarding-disabled workspaces fall
back to Orders. Loaded content remains visible during background refreshes;
empty states appear only after loading completes.

The staff panel must:

* offer public email/password registration that creates a tenant and its first
  administrator after email verification, then requires first-location creation
  in the dashboard before the administrator can use the workspace;
* require an authenticated internal account;
* show only locations and orders accessible to the account;
* allow tenant administrators to switch between locations or view an aggregate queue;
* create numbered orders for an accessible location and display their customer QR codes;
* allow an optional label and items from the location's [menu](menus.md)
  before creation;
* allow tenant administrators to manage every location's menu and location
  managers to manage their own location's menu;
* display only active queues by location or authorized tenant-wide scope,
  receiving current queue snapshots and other staff/integration changes through
  authenticated SSE, with stream access rechecked at least every five minutes;
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

The panel application provides a public restaurant-owner landing page at `/`
explaining QR-based order tracking and queue management without physical
pagers or a customer app download. Its plain-language Polish copy covers how
it works in three steps, benefits for owners and guests, a short FAQ and a
registration call to action, and never promises more than the product does
(iPhone notifications require adding Kairos to the Home Screen). An interactive
demo runs entirely in the browser without API calls: it holds one order at a
time that the visitor adds, scans with a simulated guest phone mirroring the
customer application, and marks ready and picked up while the phone shows the
resulting notifications. Public registration lives at
`/registration`, sign-in at `/login`, the staff workspace at `/dashboard` and
password change at `/dashboard/password`. Signed-in visitors to `/` or `/login`
are redirected to `/dashboard`; signed-out visitors to staff routes go to
`/login` after the session check. Authentication failures leave the public
landing page usable and keep the existing recovery state on staff routes.
Contacting the creator or receiving an invitation is not required to register.

The active queue stays primary: administrative capabilities must not compete
with frequent order creation, QR presentation or valid order transitions merely
because the signed-in account may use them.

The workspace destinations are Orders, Locations, Menu, Accounts and
Integrations; only authorized destinations are present. Locations is available
only to tenant administrators; Menu to administrators and managers. Appearance
and Account are utilities rather than workspace destinations.

An administrator with no non-archived location must create one in a required
first-location modal before using the workspace. The panel waits for
account-scoped authoritative location data before showing either onboarding or
the workspace; a failed initial request shows Retry and does not permit skipping
onboarding. A disabled location still counts as existing.

The Account menu offers Change password and Sign out. Sign out signs out the
current device immediately without confirmation; a failure keeps the session
and shows a dismissible error, and a pending logout prevents repeat
submissions. There is no separate sign-out-everywhere action: changing the
password ends every session. The Change password page retains validation errors
and explains that success signs out every device; success leads to `/login`,
which confirms the change.

The Menu page edits the menu of a selected location as one item per line, with
Save, hold-confirmed Delete and copying from another location
([menus](menus.md#location-menus)). The Locations collection includes enabled
and disabled locations and omits archived ones. Operational selectors and new
Account Invitation, API Key and webhook-subscription forms offer only enabled
locations.

#### Orders and customer QR

The active queue shows **In preparation** and **Ready** sections with counts.
Order creation opens a two-step flow. When the location has a menu, step 1
adds one item per tap, up to 99 of each; menus with more than 12 items add a
search that ignores case and diacritics, where Enter adds the single match and
clears the search. **Next**, showing the total item count, is always available,
including with no items. Step 2 holds the label, prefilled from the items as
described in [menus](menus.md#ordering-from-a-menu) and editable independently,
with a restore-from-items action when it differs. Back keeps the selection.
Without a menu, or when the menu cannot load, the flow opens at step 2.
Successful creation continues directly into the order details, keeping the
queue as context.

When a tenant has no enabled location, whether none have been created or every
existing location is disabled, Orders replaces its queue and creation control
with a direct message and **Create location** action. It opens the same
location-creation flow as Locations without leaving Orders; success keeps
Orders visible and selects the new location for the first order.

Order details show the order number, its label when present, the customer QR
code to scan again and the order's menu items with quantities. Details do not
repeat queue status or elapsed time.

Each active-order card shows its elapsed waiting time, updated approximately
once per minute, as supporting information below the order number and next
valid action. Creator attribution is not shown. The whole card opens its order
details. The card exposes the next valid status, labeled **Ready** or
**Complete**, and cancel directly rather than in an overflow menu. Canceling
requires a concise confirmation that identifies the order and distinguishes
keeping it from the terminal destructive action.

#### Management collections and details

Locations, Accounts and External Integrations show card collections and their
creation controls, with no automatic selection. Selecting a card opens its
details and applicable management actions. There is no collection search,
filtering or density control in this increment.

New member access begins in an invitation form capturing recipient email,
fixed location, and manager or operator role. Kairos emails the resulting link;
the panel exposes no link or Copy action. Creating for an email with an active
invitation requires replacement confirmation. Resend also requires confirmation
and replaces the old invitation with a new token and seven-day expiry. The caller
must have authority over both old and new targets.

Accounts includes non-archived created accounts and invitation history,
showing recipient, role, location, creation/expiry and status as defined
in [invitation cards](accounts-invitations.md#invitation-cards-and-synchronization).
Revoked, replaced and expired invitations remain listed, newest first, without
sorting or paging. Managers see only operator invitations for their location;
administrators see the tenant. Active pending invitations expose Resend even
when delivery failed; expired entries use the new-invitation form. Details retain
a confirmed Revoke action.

Account details offer the permitted status and Delete actions. Disable and
Delete use hold confirmation. Both enabled and disabled manageable accounts can
be deleted.

Integration details hold API Keys and Webhooks with their one-time secret
handling.

Entity details list editable fields, then read-only facts. Lifecycle actions
(Disable or Enable, Delete, and invitation Revoke/Resend) take effect
immediately, while editable entities commit every changed field together with
one Save. Save is unavailable until something changed and closes the details on
success; a failed save keeps the draft and shows the error. Locations,
Accounts, External Integrations and Webhook Subscriptions show an Enabled or
Disabled status; API Keys have none.

Location cards are ordered enabled first, then by display name, with stable
identity as a tie-breaker. Location details edit the name and Google review link
and show the location's menu item count with an Edit action that opens Menu for
that location. Delete is unavailable until the location is disabled and another
non-archived location exists; details explain that the last location can be
disabled but cannot be deleted. There is no time-zone editor.

If confirmed location disablement is rejected because the location still has
active orders, the location remains enabled and the panel explains that those
orders must be completed or canceled first. A direct **View orders** action
opens Orders filtered to that location.

Accounts and Integrations remain available when all existing locations are
disabled. Account Invitation creation is then unavailable, with inline guidance
to enable a location. External Integrations may still be created, while API Key
and webhook-subscription creation remains unavailable with inline guidance
until an enabled location exists.

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
Accounts, Integrations and Webhook Subscriptions; revoking Account Invitations;
deleting API Keys; retiring overlapping webhook secrets; and deleting managed
resources. The action stays within the existing detail popup or inline section,
with consequence text identifying the affected resource. It never opens a second
confirmation popup. Order cancellation retains its existing focused confirmation
flow.

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
