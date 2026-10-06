# Authentication redesign discussion

The user reopened the earlier authentication requirements on 2026-10-05.
The preferred provider is self-hosted ZITADEL. The goal is a secure, professional
solution with the least complexity justified by Kairos's real needs. Prior
Google/Clerk implementation decisions are historical context, not constraints.
The design discussion is complete. The accepted implementation replaces the
transitional provider code; docs/REQUIREMENTS.md §4.1 is the canonical current
contract. Automated checks are separate from live provider acceptance.

Accepted on 2026-10-06:

* Users enter email/password on Kairos's own page; the supplied ZITADEL login
  page is not the intended user experience.
* Spring manages browser authentication with a secure HttpOnly session cookie.
  JWT browser authentication is not required.
* Start with a simple single deployment and tolerate planned maintenance
  outages. High availability is deferred until justified.
* Email verification is out of scope for the current increment. Registration
  and password login must not require it. Preserve truthful unverified email
  state; never mark an email verified merely to bypass an onboarding check.
  Reconsider verification when its product/security purpose is agreed.

Additional decisions on 2026-10-06:

* Email/password only. Google sign-in is completely out of scope now.
* Public registration creates a tenant and its first administrator immediately;
  members join through manually shared, fixed tenant/location/role invitations.
* Kairos owns restaurant memberships and permissions. Do not duplicate these
  in ZITADEL organizations/roles.
* Store browser session state durably in PostgreSQL so service restarts do not
  discard authentication. Spring Session JDBC is the recommended standard
  implementation; Redis is not required for session storage. Session records
  contain authenticated identity, timestamps, expiry, and attributes, not only
  an ID.

Session and recovery decisions on 2026-10-06:

* A rolling inactivity timeout supersedes the earlier no-timeout choice. Use
  the previously recommended 30 days as the working duration. Successful
  authenticated requests renew it; daily use keeps the user signed in. There
  is no absolute session deadline. Service restarts do not discard sessions.
* Persistent browser sign-in is intended. Browser cookie retention is finite
  and browser-controlled; renew cookie retention with session activity so it
  does not expire while the server session remains active. Clearing/loss of
  cookies can require login.
* Ordinary logout invalidates the current browser session. Sign out everywhere
  invalidates all Kairos sessions belonging to the Account.
* Password recovery is deferred until email verification is introduced.
* Secure/HttpOnly cookies, CSRF protection, and login-time session-ID rotation
  remain required. An inactivity timeout does not stop an attacker who actively
  uses a stolen session; explicit revocation remains necessary.
* Q11 accepted: check ZITADEL session validity on every protected staff request
  so provider-side account disabling and password changes invalidate existing
  Kairos authentication. Require the expected provider user ID, verified
  password factor, and valid optional expiration; HTTP 200 alone is insufficient.
  Definitive invalidity invalidates the local session. Provider unavailability
  denies business operations with a temporary error while preserving the local
  session for retry. No cache or event synchronization initially.
* Q12 accepted: check current Kairos Account eligibility and relevant restaurant
  permissions in PostgreSQL on every authorized request. Permission changes
  take effect without waiting for login or session expiry. Keep queries simple.

Accepted integration (Q13):

Kairos form -> Spring API -> ZITADEL Session API password check -> Spring session.
Provider integration stays on the server. The service account is the session
creator, so provider session bearer tokens can be discarded; persist only the
provider session/user IDs alongside the Kairos session. Spring must establish a session
only after the provider confirms the intended user and successful password
factor, not merely after creating a provider session. This avoids adding an
OIDC redirect/callback flow solely to support one application's own login page;
the Session API is provider-specific, and future federation may change this.

Deployment and migration decisions on 2026-10-06:

* Q14 accepted: add self-hosted ZITADEL to `compose.yaml` during implementation.
  Use the existing PostgreSQL server with a separate ZITADEL database and
  database user, keeping provider data and permissions separate from Kairos.
* Q15 accepted: treat the repository as fresh and never deployed. No existing
  Account, password, or restaurant-data migration is needed. Modify the initial
  V1 schema directly and remove obsolete transitional migrations rather than
  carrying compatibility code. Existing local development data is disposable;
  this does not authorize resetting running containers or volumes now.
  This policy is persisted in `AGENTS.md` until the user changes it.

Invitation and password-policy decisions on 2026-10-06:

* Q16 accepted: manually shared invitations expire seven days after issuance,
  are revocable and single-use, and fix the tenant/location/role. They are not
  bound to a recipient email; the recipient supplies email/password. Possession
  of the link permits redemption. Store only a hash of the invitation secret
  and consume it atomically with Account creation and assignment.
* Q17 accepted: invitations create new Accounts only. Existing Accounts cannot
  redeem them to add another location or role; multiple memberships are outside
  this increment.
* Q18 accepted: ZITADEL owns and enforces password requirements. Kairos displays
  matching guidance and safe provider validation errors rather than maintaining
  a competing backend password policy. Email/password input is bounded by the
  provider's 200-character limit; the initial password minimum is 12 characters
  without forced character classes or scheduled password expiry.

Password change and cleanup decisions on 2026-10-06:

* Q19 accepted: signed-in users can change their password by supplying their
  current password. ZITADEL verifies the current password and enforces the new
  password policy. Successful changes invalidate all Kairos sessions for the
  Account, including the current session, and require fresh login. Forgotten-
  password recovery remains deferred and is not needed for this operation.
* Q20 accepted: remove obsolete JWT/access-token/refresh flows, verification-
  gated onboarding, Google/Clerk integration, and custom multiple-tab auth
  coordination. Use ordinary shared HttpOnly session-cookie behavior across
  tabs, retaining CSRF protection and Account-scoped frontend state. No custom
  tab locking or refresh coordination is needed. Logout invalidates the server
  session; other tabs discover this on their next protected request.

No further product decisions are open in this discussion. Remaining deployment
mechanics are implementation details, subject to the accepted simple Compose
configuration. Implementation must still validate the provider behavior below.
Provider revocation behavior must be tested against the selected ZITADEL release;
current source clears the password factor on password change and removes session
projections on user disabling or termination.

Spring Session JDBC reference:
https://docs.spring.io/spring-session/reference/configuration/jdbc.html

Facts checked in primary documentation:

* ZITADEL supplies a Spring Boot OIDC example using Spring Security and
  server-managed sessions: https://zitadel.com/docs/sdk-examples/spring
* Current Compose deployment includes the ZITADEL API, separate Login UI,
  PostgreSQL, and reverse-proxy routing:
  https://zitadel.com/docs/self-hosting/deploy/compose
* Self-hosted infrastructure and backups are operator responsibilities:
  https://zitadel.com/docs/self-hosting/manage/production
* Authentication email delivery needs a configured notification provider:
  https://zitadel.com/docs/guides/manage/customize/notification-providers


Custom login reference:
https://zitadel.com/docs/guides/integrate/login-ui/username-password
Session validation reference:
https://zitadel.com/docs/guides/integrate/login-ui/session-validation
