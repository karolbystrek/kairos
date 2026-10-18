# Accounts and invitations

## Accounts and invitations

An **Account Invitation** is a separate, single-use bearer capability that
authorizes creation of one person-oriented manager or operator account. It is
not an incomplete account. Creation requires a valid recipient email, normalized
with the same rules as Account email, and records it alongside the owning tenant,
fixed location and assignment role, and issuing account. The invitation fixes
that email but does not reserve a globally unique Account identity before
redemption. Kairos emails the link to that mailbox; possession proves access to
the invited email. Redemption marks that email verified without a
separate code step. Redemption displays the email read-only and
collects password and confirmation. The API rejects a different normalized email
before creating an identity and rechecks the binding during atomic redemption.
It then atomically creates the identity and an enabled member account
with an assignment to the enabled location while consuming the invitation. No
further administrator approval is required.

### Expiration and bearer handling

An invitation expires exactly seven days after issuance. Opening its
registration page neither reserves it nor extends that deadline. Multiple
pending invitations may target the same location and role, but at most one
active invitation exists per tenant and normalized recipient email. Creation
and resend enqueue a Polish Resend email with the fresh invitation link,
role, location and inviter. The subject is **Zaproszenie do restauracji
zarządzanej przez {administrator email}**; no tenant display name exists. The panel and creation
response expose no token or Copy link action. The database stores the SHA-256
hash of a 32-byte random Base64url token; the pending email payload contains the
recoverable token only in encrypted form and clears it on terminal delivery.
The emailed link uses
`/account-registration#invitation=<token>` so the secret is not sent
in the initial navigation URL, gateway logs, or referrer; the client reads the
fragment and supplies the token only in the API request body.

Expiration is derived from the authoritative `expiresAt` whenever invitations
are listed, previewed, or redeemed. PostgreSQL persists only `PENDING`,
`REDEEMED`, `REVOKED`, or `REPLACED`; a pending row whose deadline has elapsed is
effectively expired and cannot be redeemed. No background worker materializes
an `EXPIRED` state, and reads do not update the row merely to record the passage
of time. The exact audit time remains the stored deadline.

### Management and issuer eligibility

Pending invitations appear alongside created accounts in the Accounts collection
and open their own details in the shared popup presentation. Tenant
administrators may inspect the whole tenant history and manage every pending
invitation in their tenant.
Managers see and manage only operator invitations to their own enabled location
regardless of which authorized manager issued them. Redemption
rechecks that the issuing account is enabled and remains authorized for the
fixed location and role; losing that authority makes its outstanding
invitations unusable. Disabling an issuing account revokes its pending
invitations in the same transaction and records an internal reason, while the
redemption-time check still protects future role or assignment changes.
Invalid registration input and email or identity conflicts do not consume an
invitation, while concurrent redemption is
serialized so exactly one successful submission can create an account. An
existing account cannot redeem an invitation to gain another location or role.

### Invitation cards and synchronization

Accounts presents created accounts and the invitation history. The API history
carries recipient, role, location, issuer, creation/expiry times, lifecycle state
and delivery state (`QUEUED`, `SENT`, `FAILED`), including terminal entries.
The panel shows one invitation status: **Pending** (active, queued or sent),
**Not sent** (active, delivery failed) or **Expired** (expired, revoked or
replaced). Redeemed invitations are not listed; their account represents them.
Administrators see the tenant; managers see operator invitations for their own
location. History is ordered newest first, with stable ordering for equal
creation times.

Creating for an already invited email requires explicit replacement confirmation
in the panel. The API returns `409` until replacement is confirmed. Replacement
requires authority over both the existing invitation and its new target; it
records the previous entry as `REPLACED`, cancels its queued delivery and creates
a new invitation with a fresh token and seven-day lifetime atomically. Resend
uses this same confirmed replacement operation. Only active pending entries
can be resent, including entries whose delivery failed; expired entries require
the ordinary new-invitation form. Previous links become unusable.

Revoke keeps the inline hold confirmation defined in
[staff panel requirements](staff-panel.md#lifecycle-vocabulary-and-confirmations).
It identifies recipient and target and makes the emailed link unusable.
Loading, errors and Retry remain visible even with no created accounts.
Popups move focus inside and return it to their invoking control when possible;
all actions support touch, pointer, keyboard and assistive technology. Mobile
layouts avoid horizontal scrolling.

Account-scoped SWR revalidates after creation, replacement, resend and revocation,
and on focus/reconnect. There is no live staff event stream or history search.

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
invitation it issued, and retains its stable identity, identity subject and
email for the [retention grace period](resilience.md#retention-purge). During
that period the email address remains reserved, and an archived account cannot
authenticate, be enabled, or be restored. Delete may transition an enabled or
disabled account directly to archived and applies all shutdown effects
atomically. After the grace period the account, its staff identity, its
assignment and the invitations it issued or redeemed are deleted.

Account Delete uses the same authorization boundary as account status
management. A tenant administrator may delete a manageable member account in
its tenant. A location manager may delete only an operator assigned to that
manager's enabled location. An administrator account, the acting account, a
peer manager, and another location's operator are never valid targets.


### Redemption and browser context

The invitation registration form displays the location prominently and the role
as supporting text, with clear spacing before the form. It displays the fixed
recipient email read-only and asks for password and confirmation. Signed-in users
must explicitly sign out first; the fragment is preserved while doing so. Successful registration creates the
Account and assignment in the same transaction as invitation consumption, then
issues cookies and opens Orders. Known unusable links have identity-free
terminal problems; unknown or malformed tokens do not disclose Account identity.

One browser profile represents one signed-in Account because tabs share cookies.
Same-account tabs are best effort, with no live UI synchronization or cross-tab
Web Lock. Different Accounts require separate profiles, private contexts, or
devices. The primary workspace supports administrator location switching and
aggregate views without depending on tabs.

A non-bypassable gateway must rate-limit login,
registration, invitation redemption, email verification/code resend,
authenticated password changes, and future password recovery.
Social login, MFA, and additional administrator invitations remain deferred.
