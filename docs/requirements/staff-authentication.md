# Staff authentication and browser sessions

## Contents

- [Staff authentication and browser sessions](#staff-authentication-and-browser-sessions)
- [Contents](#contents)
- [4. Authentication and Security](#4-authentication-and-security)
- [4.1 Staff authentication](#41-staff-authentication)
- [Registration and first-location onboarding](#registration-and-first-location-onboarding)
- [Browser cookies and durable sessions](#browser-cookies-and-durable-sessions)
- [Provider validation and revocation](#provider-validation-and-revocation)
- [Panel session behavior](#panel-session-behavior)

## 4. Authentication and Security

### 4.1 Staff authentication

Staff authentication uses self-hosted ZITADEL through Spring's backend-only
Session API integration. Users enter email/password on the Kairos panel; no
hosted provider login redirect, browser SDK, social login, or JWT/refresh flow
is used. Kairos owns tenants, Accounts, memberships and authorization. All
Accounts belong to one tenant. Username, local password hashes, Platform
Operator, Tenant Registration Invitations, and verification staging are removed.

#### Registration and first-location onboarding

Public registration collects email, password, and confirmation in one focused
account form. Submitting it immediately validates and provisions the identity,
commits one tenant and administrator locally, establishes the browser session,
and opens the staff workspace at `/dashboard`. Registration does not collect or
create a location.
The registration form has no step counter, progress bar, Next/Back controls, or
persistent password-requirement paragraph. Account fields retain submitted values
on recoverable failure and show corrective errors beside the affected control.
Public Sign in ends with **Don't have an account? Create one.**, with only
**Create one.** linked to the public `/registration` page. Registration ends with **Already have an
account? Sign in.**, with only **Sign in.** linked to `/login`.

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
counts as completed onboarding. Future email verification can precede the same
gate; verification itself remains outside the current increment.

Managers/operators register through manually shared member invitations. Email
verification and forgotten-password recovery remain deferred; provider email
remains truthfully unverified and registration sends no email. Initial provider
policy is a minimum of 12 characters without mandatory character classes or
scheduled password expiry. Registration validates that configured minimum,
email syntax, confirmation, and the 200-character input bounds at submission
in the panel and API. ZITADEL remains authoritative for provider password policy;
known provider password-complexity failures are translated to safe Password
field messages, and identity conflicts to Email field messages. Provider response
bodies, internal identifiers, and submitted credentials never become UI errors.

#### Browser cookies and durable sessions

Spring Session JDBC stores browser sessions durably in PostgreSQL. A secure,
HttpOnly, host-only `__Host-session` cookie with `Path=/` and `SameSite=Lax`
carries only an opaque session ID. Successful authenticated requests renew the
30-day inactivity lifetime and cookie retention. There is no absolute deadline
for active users; service restarts preserve sessions. Browser cookie deletion
can require login. Login replaces the previous browser session with a new ID.
Provider session/user IDs remain server-side in JDBC session attributes; the
provider session bearer token is discarded because the service account created
the session and can retrieve/terminate it without that token.
provider credentials and passwords never appear in browser
JavaScript, browser storage, API response bodies, URLs or logs. CSRF metadata is
separate: `__Host-XSRF-TOKEN` and the `X-XSRF-TOKEN` header protect unsafe browser
requests, including login/registration/logout. Credentialed CORS remains scoped
by frontend and resource family. No request-body/provider-response logging is
used for authentication.

#### Provider validation and revocation

Each protected staff request retrieves the ZITADEL session and requires the
expected provider user ID, successful password factor and absent/future expiry.
HTTP 200 alone is insufficient: password changes can remove the password factor.
Definitively invalid provider state invalidates the local session and returns
`401`; provider outages return `503` without discarding the session. Current
Account eligibility, role, Location Assignment and location status are checked
in PostgreSQL, with no permission cache, provider cache or event synchronization.
Email alone never grants authority. This deliberately adds a provider request
and database work to each protected request; optimize only when measured need
justifies the revocation delay or added complexity.

Ordinary logout invalidates the current browser session and attempts provider
session termination. Sign out everywhere deletes all indexed Kairos sessions for
the Account and records a cutoff to reject an in-flight stale session. Signed-in
password changes supply the current password to ZITADEL; success invalidates all
Kairos sessions, including the current one, and requires fresh login. Provider
sessions that have no remaining Kairos session grant no access to Kairos.

#### Panel session behavior

The panel performs no refresh or authentication replay. An unauthorized response
clears staff state; CSRF recovery is bounded to one bootstrap and retry, while
server errors leave the session available for retry. Staff SWR keys remain scoped
by Account ID and staff state is cleared on account change. Tabs share the normal
cookie; there is no custom tab locking or live auth synchronization. Other tabs
observe logout on their next protected request. Automated/background protected
requests count as activity; tracking mouse/keyboard activity is not required.
