# Location lifecycle and cascades

## Contents

- [Location lifecycle and cascades](#location-lifecycle-and-cascades)
- [Contents](#contents)
- [Location lifecycle and cascades](#location-lifecycle-and-cascades-1)

## Location lifecycle and cascades

A **Location** is an enabled, disabled, or archived physical restaurant owned by
one tenant. A newly registered tenant may have no location until its
administrator completes mandatory first-location onboarding. After that, its
last non-archived location cannot be deleted. A tenant may have no enabled
locations after all its locations are disabled.
Only a tenant administrator manages the location lifecycle. The editable
location properties are its display name and optional Google review link; its IANA time zone remains fixed at `UTC`. Enabled and disabled
location names are unique within a tenant after trimming and case
normalization. An archived location releases its name for reuse.

The Google review link is absent by default, disabling invitations. First-location
onboarding and subsequent creation accept the optional link; administrators can
change or remove it in location management. Only HTTPS links on the supported
Google Maps/review hosts are accepted, without credentials or fragments and with
at most 2048 characters. Review invitation behavior is defined in
[customer requirements](orders-customer.md#google-review-invitations).

Disabling a location makes the location and everything scoped to it ineligible
for operational use. Every non-archived manager or operator account assigned to
that location is persistently changed to `DISABLED`; tenant-administrator and
archived accounts are never included in this cascade. Earlier authentication for
an affected account is invalidated by its authentication cutoff, and every pending Account Invitation that
targets the location or was issued by an affected account is revoked. A location
with an `IN_PREPARATION` or `READY` order cannot be disabled; the administrator
must complete or cancel every active order first.

Re-enabling a location persistently changes every non-archived account assigned
to it to `ENABLED`, including an account that was disabled independently before
the location was disabled. This deliberately favors one simple bulk lifecycle
over remembering or restoring prior account states. Archived accounts remain
archived, and pending invitations are not restored; staff issue new invitations
when needed.

Location disablement makes only that location ineffective within a
multi-location API Key or webhook subscription. The credential or subscription
and its other enabled-location grants remain usable. A configuration whose only
location is disabled remains stored but cannot perform location-scoped work or
receive new deliveries until that location is re-enabled.

Deleting a location archives it internally rather than physically removing its
identity or historical records. It also archives every non-archived assigned
member account, invalidates earlier authentication and revokes pending invitations, and removes those accounts from
the ordinary Accounts collection. Completed and canceled orders retain their
location association and remain anonymously readable through their existing
customer tracking references after location disablement or archival.

Only a disabled location can be deleted, and at least one other non-archived
location must remain in the same tenant. Archived locations and other tenants'
locations do not count. Location creation and deletion serialize on the tenant
row before deletion locks the target and checks the remaining count, so concurrent
deletions cannot remove the final location. Rejected deletion applies no cascade.
Repeating Delete for an already archived location remains idempotent.
Deletion is an irreversible archival transition and an archived location cannot
be restored. Archived member accounts retain and reserve their normalized email addresses and provider subjects for
unambiguous historical attribution. They cannot authenticate or be restored.

Archiving a location permanently removes it from API Key location grants and
webhook subscription location selections. An API Key left without any location
grant is revoked, and a webhook subscription left without any location
selection is archived. Multi-location configurations retain their other
location relationships and lifecycle state.
