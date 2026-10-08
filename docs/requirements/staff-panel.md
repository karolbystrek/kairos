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
  administrator immediately, then requires first-location creation on the main
  page before the administrator can use the workspace, without email verification;
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

#### Management collections and details

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
single name field visually unlabeled and uses the direct **Integration name**
placeholder while retaining an assistive-technology label.

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
visible but unavailable until the location is disabled and at least one other
non-archived location exists. The detail explains that the last location can be
disabled but cannot be deleted. The detail uses no
time-zone editor or unrelated operational fields.

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
uses the check, and Delete uses the trash symbol. Their compact icon-only
controls share the same size, corner treatment, accessible naming, and pointer
tooltip. Focused confirmation dialogs retain explicit text labels for
consequential actions.

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
