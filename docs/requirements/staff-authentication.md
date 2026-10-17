# Staff authentication and browser sessions

## Authentication and security

### Staff authentication

The API owns staff authentication. Users enter email/password on the Kairos
panel; the API verifies a PBKDF2 password hash stored in PostgreSQL and issues a
server-side session. No external identity provider, login redirect, browser SDK,
social login, or JWT/refresh flow is used. Kairos owns tenants, Accounts,
memberships and authorization. All Accounts belong to one tenant. Usernames,
Platform Operator and Tenant Registration Invitations are removed.

JWTs are not used because password change and account disabling must revoke
access immediately.

#### Registration and first-location onboarding

Public registration collects email, password, and confirmation in one focused
account form. Submitting it validates and provisions an unverified identity, then sends a
6-digit verification code. No tenant, administrator or tenant authority exists
until the correct code is confirmed. Confirmation commits the tenant and
administrator, establishes the browser session and opens `/dashboard`. Registration does not collect or
create a location.
`EMAIL_VERIFICATION_ENABLED` defaults to `true`. Local `.env.example` sets it to
`false`: registration and unverified sign-in then mark the identity email verified
and complete tenant/admin onboarding without a code screen or new verification
email. Existing pending code delivery is canceled on completion. Identity and
Account eligibility checks still apply, and invitation redemption is unchanged.
Re-enabling verification does not undo emails already marked verified.

Registration is a single form without steps. Account fields retain submitted
values on recoverable failure and show corrective errors for the affected
control. Public sign-in links to `/registration`, and registration links back to
`/login`.

The panel root `/` is the public restaurant-owner landing page. It checks the
existing API session in the browser and redirects signed-in visitors to
`/dashboard` without blocking public content when the check fails. `/login`
also sends signed-in visitors to `/dashboard`. Signed-out access to `/dashboard`
goes to `/login`, including after sign-out or session expiry; successful sign-in
opens `/dashboard`. These redirects are navigation conveniences; API
authorization remains authoritative.

In the dashboard, an administrator whose tenant has no non-archived location
must create the first location in the existing focused location-creation modal.
Its required mode has no Close or Cancel action and cannot be dismissed with
Escape or an outside click. Only successful location creation completes the
onboarding and opens Orders selected to the created location. Failed creation
retains the modal and entered name. Completion is derived from the server's
saved location collection, with no browser flag or additional persisted
onboarding status. Reload, subsequent sign-in, and direct dashboard navigation
repeat the same check. This gate applies only to administrators; invited members
already have a fixed location and do not create one. A disabled saved location
counts as completed onboarding. Email verification precedes this same gate.

A new code can be requested after a 60-second cooldown. Codes expire after one
hour, are stored hashed, only the latest code is valid, and at most five wrong
attempts are allowed per code. Requesting a new code does not reset the total:
after 15 wrong codes for one email (registration and password reset counted
separately, in Redis) code entry returns HTTP 429 for an hour. Expired or
incorrect codes show safe corrective errors. Signing in with an unverified identity without an
Account resumes verification. Registering an email held by such an abandoned
identity replaces it; an existing Account is never replaced.

Managers/operators register through emailed, recipient-bound member invitations.
Redemption verifies the fixed email without a separate code step.

Forgotten passwords are reset from the login page with an emailed 6-digit code,
under the same code rules as registration (one-hour lifetime, hashed, latest
code only, five wrong attempts per code, 60-second resend cooldown, one-hour
lock after 15 wrong codes in total). The request answers
identically whether or not the email belongs to an Account that may sign in, and
disabled or archived Accounts receive no code. A correct code sets the new
password, marks the email verified and revokes every existing session of the
Account; the user then signs in. An Account without stored credentials (created
before the API owned them) gains them through this flow.

The password policy is a minimum of 12 characters
without mandatory character classes or scheduled password expiry. Registration
and password change validate that minimum, and registration also email syntax,
confirmation and the 200-character input bounds, in the panel and API. Identity
conflicts become Email field messages. Internal identifiers, password hashes and
submitted credentials never become UI errors.

#### Browser cookies and durable sessions

Spring Session JDBC stores browser sessions durably in PostgreSQL. A secure,
HttpOnly, host-only `__Host-session` cookie with `Path=/` and `SameSite=Lax`
carries only an opaque session ID. Successful authenticated requests renew the
30-day inactivity lifetime and cookie retention. There is no absolute deadline
for active users; service restarts preserve sessions. Browser cookie deletion
can require login. After 10 failed sign-ins for one email from one client address within 15 minutes
(counted in Redis by email hash and address), further sign-in attempts from that
address return HTTP 429 without checking the password until the window expires;
other accounts and other addresses are unaffected. A per-email ceiling of 100
failures per window from all addresses together also returns 429. A completed
password reset clears the email's counters. Login replaces the previous browser session with a new ID.
The staff identity subject remains server-side in JDBC session attributes.
Password hashes and passwords never appear in browser
JavaScript, browser storage, API response bodies, URLs or logs. CSRF metadata is
separate: `__Host-XSRF-TOKEN` and the `X-XSRF-TOKEN` header protect unsafe browser
requests, including login/registration/logout. Credentialed CORS remains scoped
by frontend and resource family. No request-body logging is
used for authentication.

#### Session validation and revocation

Each protected staff request resolves the session's identity subject to its
Account and rejects sessions created before the Account's authentication cutoff.
Current Account eligibility, role, Location Assignment and location status are
checked in PostgreSQL, with no permission cache or event synchronization.
Invalid or revoked state invalidates the local session and returns `401`. Email
alone never grants authority.

Ordinary logout invalidates the current browser session. Signed-in password
changes require the current password; success stores the new hash, deletes all
indexed Kairos sessions for the Account, including the current one, records a
cutoff to reject an in-flight stale session, and requires fresh login. There is
no separate sign-out-everywhere action.

#### Panel session behavior

The panel performs no refresh or authentication replay. An unauthorized response
clears staff state; CSRF recovery is bounded to one bootstrap and retry, while
server errors leave the session available for retry. Staff SWR keys remain scoped
by Account ID and staff state is cleared on account change. Tabs share the normal
cookie; there is no custom tab locking or live auth synchronization. Other tabs
observe logout on their next protected request. Automated/background protected
requests count as activity; tracking mouse/keyboard activity is not required.
