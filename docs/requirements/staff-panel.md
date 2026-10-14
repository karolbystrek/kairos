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

The dashboard keeps its selected workspace in the URL fragment (for example,
`#integrations`). Refresh, direct links and browser Back/Forward retain that
selection. Missing, unknown, unavailable or onboarding-disabled workspaces fall
back to Orders. Initial content loading uses a small accessible loading indicator, with no
skeletons or placeholder queue headings. Loaded content remains visible during
background refreshes; empty states appear only after loading completes.

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

The panel application provides a public restaurant-owner landing page at
`/`. It briefly explains QR-based order tracking and queue management
without physical pagers or a customer app download. Its primary action links to
public registration at `/registration`, while Sign in links to `/login` and the
staff workspace lives at `/dashboard`, with password change at
`/dashboard/password`. Signed-in visitors to `/` or `/login` are redirected to
`/dashboard`; signed-out visitors to staff routes go to `/login` after the
session check. Authentication failures leave the public landing page
usable and keep the existing recovery state on staff routes. The creator's email
contact appears as secondary footer information; contacting the creator or
receiving an invitation is not required to register. The page uses spacious typography and the shared
responsive, accessible light/dark design language.

The staff experience keeps the active queue visually primary. Administrative
capabilities must not compete with frequent order creation, QR presentation,
or valid order transitions merely because they are available to the signed-in
account.

Orders, Locations, Menu, Accounts, and Integrations use one adaptive tab-style
navigation system: a centered rounded segmented control on tablet and desktop and bottom
navigation on narrow mobile layouts. Tabs share equal widths determined by the
visible destinations. Tablet and desktop show icons beside labels; phones show
icons above small labels. The bar scrolls horizontally when destinations do not
fit, preserving readable labels and touch targets. Only authorized destinations
are present.
An administrator with no non-archived location first sees the required
first-location modal over the rendered workspace. The workspace remains visible
through the shared blurred backdrop but cannot be interacted with while the
required modal is open. The panel waits for
account-scoped authoritative location data before showing either onboarding or
the workspace; a failed initial request shows Retry and does not permit skipping
onboarding. A disabled location still counts as existing.
The shell and visual treatment do not vary by role. Appearance and Account
remain trailing icon-only utilities on the same level rather than peer workspace
destinations. Account opens a menu containing Change password and, separated
and last, Sign out. Sign out signs out the current device immediately without a
confirmation; a failure keeps the session, shows a dismissible error in the
panel, and pending logout prevents repeat submissions. There is no separate
sign-out-everywhere action: changing the password ends every session. Change
password opens its own page in the authentication-page layout with a link back
to the panel; it omits persistent password-length guidance, and retains
validation errors and the explanation that success signs out every device.
Success leads to `/login`, which confirms the password change.

Locations appears between Orders and Accounts for tenant administrators and is
absent for managers and operators. Menu follows it for administrators and
managers: a location selector, shown when more than one location is available,
chooses whose menu the page edits as one item per line with Save, a hold-to-confirm
Delete and a copy-from-another-location selector. The Locations collection includes enabled
and disabled locations and omits archived locations. Operational selectors and
new Account Invitation, API Key, and webhook-subscription forms offer only
enabled locations.

#### Orders and customer QR

The active queue uses adjacent **In preparation** and **Ready** sections on
tablet and desktop and the same two sections stacked vertically on mobile.
These sections use headings, counts, whitespace, and a shared separator rather
than large rounded lane containers. Order creation begins from one compact,
icon-and-text creation action at the start of the active workspace and opens an
extra-large popup that fills the screen on phones. When the location has a
menu, step 1 shows its items as tiles in menu order: each tap adds one,
up to 99; a selected tile shows its quantity, a distinct state and a decrease
control, tinted red. Menus with more than 12 items add a search field, ignoring case and
diacritics, that is focused automatically only with a precise pointer; Enter
adds the single match and clears the search. A **Next** action with the total
item count is always available, including with no items. Step 2 holds the label,
prefilled from the items as described in [menus](menus.md#ordering-from-a-menu)
and editable independently, with a restore-from-items action when it differs.
Back keeps the selection; Create occupies the Next position, and steps switch
in place with a short cross-fade that respects reduced motion. Without a
menu, or when the menu cannot load, the flow opens at step 2.
Successful creation continues directly into the order details popup.
The underlying queue remains visible to preserve context. The Orders, Locations, Accounts, and Integrations creation actions
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

Order details show the centered order number with its label beneath when
present, the dominant customer QR code to scan again, and the order's menu items
with quantities. On wide screens the items sit left of the QR code, top-aligned,
with their heading kept visible while the list scrolls within the QR code's
height; narrow screens stack the QR code first and the popup scrolls as a whole. Details do not repeat queue status, elapsed
time, explanatory copy, or a redundant footer action.

Canceling an order requires a concise confirmation that identifies the order
and distinguishes keeping it from the terminal destructive action.

Each active-order card shows its elapsed waiting time, updated approximately
once per minute. The duration is supporting information: the prominent order
number beside its quieter label and the next valid action remain more
prominent. Creator attribution is not shown. The
complete compact card remains a direct target for opening its order details,
without a duplicate QR control. One trailing action group exposes two large
controls directly: the next valid status, labeled **Ready** or **Complete**, and
cancel. Cancel retains its order-specific confirmation rather than being hidden
in an overflow menu.

#### Management collections and details

Locations, Accounts, and External Integrations initially show only
responsive card collections and relevant creation controls, with no automatic
selection or persistent right-hand details. Selecting a card opens details and
applicable management actions in the shared centered popup, adapted to available
mobile space. There is no collection search or filtering in this increment.
Collections use one consistent responsive card size with no density controls
or saved density preference. Phones use one full-width column.

Popups are sized by content: confirmations small, single-purpose forms medium,
entity and order details large, and order creation extra large.

New member access begins in an invitation popup capturing recipient email,
fixed location, and manager or operator role. Kairos emails the resulting link;
the panel exposes no link or Copy action. Creating for an email with an active
invitation requires replacement confirmation. Resend also requires confirmation
and replaces the old invitation with a new token and seven-day expiry. The caller
must have authority over both old and new targets.

Accounts includes non-archived created accounts and invitation history,
showing recipient, role, location, creation/expiry and one status chip as defined
in [invitation cards](accounts-invitations.md#invitation-cards-and-synchronization).
Revoked, replaced and expired invitations remain listed; entries appear
newest first without sorting or paging controls. Managers see only operator
invitations for their location;
administrators see the tenant. Active pending invitations expose Resend even
when delivery failed; expired entries use the new-invitation form. Details retain
a confirmed Revoke action. Accessible labels expose status and shared popup
focus, touch targets and press feedback remain consistent.

Selecting an existing account
opens its details and permitted status and Delete actions. Disable retains its
hold confirmation; Delete uses hold confirmation. Both enabled and disabled
manageable accounts can be deleted.

Integration details retain the compact segmented control between API Keys and
Webhooks and their one-time secret handling.

Every entity detail popup uses one sheet layout. The header row holds the title
and its status chip, as tall as the title line, at the top left, level with Close. The body lists editable
fields first, then read-only facts that are not already in the title. The footer
holds immediate lifecycle actions (Disable or Enable, Delete, and invitation
Revoke/Resend) and, for editable entities, one **Cancel**/**Save** pair that
commits every changed field together. Footer actions share one equal-width
grid, two columns from small screens up, with lifecycle actions above
Cancel/Save. Save is unavailable until something
changed and closes the popup on success; a failed save keeps the draft and
shows the error. Locations, Accounts, External Integrations and Webhook
Subscriptions show an Enabled or Disabled chip; API Keys have none.
Contextual labels omit a repeated
entity name when the popup makes the target unambiguous. The new-integration
popup uses the direct **Integration name** placeholder with an accessible label.

Location cards retain enabled-first display-name ordering with stable identity
as a tie-breaker. Location details edit the name and Google review link and commit
them with Save, and show the location's menu item count with an Edit action that
opens Menu for that location. The footer contains Disable or Enable, and
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
