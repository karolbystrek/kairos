# Accounts and invitations

## Contents

- [Accounts and invitations](#accounts-and-invitations)
- [Contents](#contents)
- [Accounts and invitations](#accounts-and-invitations-1)
- [Expiration and bearer handling](#expiration-and-bearer-handling)
- [Management and issuer eligibility](#management-and-issuer-eligibility)
- [Invitation modal and synchronization](#invitation-modal-and-synchronization)
- [Account authority and archival](#account-authority-and-archival)
- [Redemption and browser context](#redemption-and-browser-context)

## Accounts and invitations

An **Account Invitation** is a separate, single-use bearer capability that
authorizes creation of one person-oriented manager or operator account. It is
not an incomplete account and reserves no recipient email. It records the
owning tenant, fixed location and assignment role, and issuing account. Anyone
who possesses its link can attempt to redeem it, and the person who completes
the first successful redemption becomes the account owner. Redemption collects
the person's email, password, and confirmation. After verified sign-in it atomically creates an
enabled member account with an assignment to the enabled location while
consuming the invitation. No further administrator approval is required.

### Expiration and bearer handling

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

### Management and issuer eligibility

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

### Invitation modal and synchronization

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

### Account authority and archival

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


### Redemption and browser context

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

A non-bypassable gateway must rate-limit login,
registration, invitation redemption, authenticated password changes, and future
password recovery. Future email verification will not substitute for throttling.
Social login, MFA, and additional administrator invitations remain deferred.
