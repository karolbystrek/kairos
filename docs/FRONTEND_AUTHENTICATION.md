# Panel Frontend Authentication

Status: implemented for local authentication, public tenant onboarding, and
scoped account provisioning.

This document explains the authentication integration in `apps/panel-app`: what
the frontend owns, how session recovery works, and which invariants future
changes must preserve. The authoritative system behavior remains in
[`REQUIREMENTS.md`](REQUIREMENTS.md), while the complete authentication
contract remains in [`AUTHENTICATION.md`](AUTHENTICATION.md).

## 1. Scope

The panel frontend implements:

* signed-out Sign in and Register tenant tabs;
* public tenant registration without automatic authentication;
* a local username/password login form;
* CSRF bootstrap and headers for cookie-authenticated unsafe requests;
* current-account loading through `GET /api/auth/me`;
* an SWR gate that mounts staff operations only for an authenticated account;
* one automatic refresh attempt after `401 Unauthorized`;
* one retry of the original request after successful recovery;
* logout and a request helper for logout-all;
* serialization of authentication-cookie mutations;
* account-scoped SWR data and cache cleanup when authentication changes in the
  current tab;
* capability-gated Orders and Accounts workspaces;
* administrator and manager account-provisioning forms.

Spring Security remains the authentication and authorization authority. The
frontend gate is a user-experience boundary, not a security boundary. Any
current or future capability-driven controls and redirects are presentation
only and never authorize an operation.

Tenant onboarding is not standalone account self-registration. It creates only
the tenant's first administrator through the dedicated Spring endpoint.
Additional administrators, later locations, account listing and status UI,
OIDC, account linking, MFA, password recovery, email verification, invitations,
CAPTCHA, and passkeys remain excluded. `logoutAll()` exists in the request
layer, but the current UI exposes only ordinary logout.

## 2. Browser Session and Panel Model

One browser profile on the panel origin represents one signed-in account. Its
tabs share the same host-only authentication cookies, so different accounts in
different tabs are unsupported. Simultaneous accounts require separate browser
profiles, private browsing contexts, or devices.

Same-account tabs may be used on a best-effort basis. The frontend prevents
cooperating tabs from rotating the same refresh credential concurrently, but it
does not keep their interface state immediately synchronized. Another tab
observes logout or an account change when it next sends a request or when SWR
revalidates on focus or reconnect.

The panel provides one primary operational workspace. Managers and operators
work with their assigned location; tenant administrators switch locations or
use an aggregate view. Browser tabs are not required for core workflows. More
Next.js routes may be introduced later when the information architecture
benefits from them without changing this browser-session model.

## 3. Component and Module Boundaries

| File | Responsibility |
| --- | --- |
| `apps/panel-app/app/page.tsx` | Server Component route that renders the client panel boundary. |
| `apps/panel-app/components/staff-panel.tsx` | Signed-out tabs, login UI, current-account SWR state, logout UI, terminal-`401` subscription, and the authenticated workspace gate. |
| `apps/panel-app/components/tenant-registration-form.tsx` | Public tenant-registration form and mutation state. |
| `apps/panel-app/components/order-management.tsx` | Authenticated panel content with account-scoped SWR keys. |
| `apps/panel-app/components/account-management.tsx` | Capability-gated manager/operator provisioning UI. |
| `apps/panel-app/src/api/account-input.ts` | Shared Zod username, email, display-name, and BCrypt password input rules. |
| `apps/panel-app/src/api/tenant-registrations.ts` | Zod contracts and anonymous tenant-registration request. |
| `apps/panel-app/src/api/accounts.ts` | Zod contracts and authenticated account-provisioning request. |
| `apps/panel-app/src/api/authentication.ts` | Zod contracts and the `/me`, login, logout, and logout-all operations. |
| `apps/panel-app/src/api/api-fetch.ts` | Shared same-origin `fetch`, Problem Details errors, CSRF behavior, session recovery, and bounded retries. |
| `apps/panel-app/src/api/auth-coordination.ts` | Authentication-cookie locking and a same-tab notification when authentication is required. |
| `apps/panel-app/src/api/cache-keys.ts` | Identification of staff-owned SWR cache entries. |
| `apps/panel-app/src/api/orders.ts` | Staff request module using the shared authenticated request client. |

`page.tsx` stays a Server Component because it has no state, event handlers, or
browser API use. `StaffPanel` is the page's authentication-specific Client
Component boundary because the authentication UI requires forms, SWR, local
interaction state, effects, and browser coordination. Authentication is not
performed by the Next.js server; the client asks Spring for the current account.

## 4. Credentials and CSRF

Requests use same-origin `/api` URLs routed by Caddy to Spring. The shared client
sets `credentials: "same-origin"`, allowing the browser to attach cookies
without exposing them to JavaScript.

The access and refresh credentials remain in `Secure`, `HttpOnly` cookies. The
frontend never reads them and never stores a password, access token, refresh
token, coordination marker, or account record in `localStorage` or
`sessionStorage`.

The readable `__Host-XSRF-TOKEN` cookie is deliberately different: it is a CSRF
token, not an authentication credential. Before every unsafe request, the
frontend ensures that this cookie exists, reads its latest value, and sends it
in the `X-XSRF-TOKEN` header. Safe requests such as `GET /api/auth/me` do not
bootstrap CSRF unnecessarily.

CSRF bootstrap is deduplicated within one tab. Public tenant registration uses
the same CSRF-aware client, but it does not acquire the authentication-cookie
lock because it neither reads nor mutates authentication state. When Spring returns a recognized
missing- or invalid-CSRF Problem Details response, the client obtains a new
token and retries that request once. Other `403 Forbidden` responses mean the
authenticated account lacks permission and never start session refresh.

## 5. Initial Account Check

On panel startup:

1. `StaffPanel` asks SWR to run `getCurrentAccount()`.
2. It sends `GET /api/auth/me`.
3. A successful response is validated with Zod and becomes the current account.
4. A `401` enters the common one-refresh recovery flow.
5. If no valid refresh session remains, a same-tab notification clears staff
   state and the panel shows the login form.

Network and server failures show an authentication-unavailable state. SWR may
retry those failures with its bounded policy, but ordinary client errors are
not retried.

## 6. Login

The login flow is:

1. Zod trims and lowercases the username and validates input lengths, including
   BCrypt's 72-byte password limit.
2. The client initializes CSRF.
3. It acquires the shared authentication-cookie lock.
4. It posts the credentials to `POST /api/auth/login`.
5. Automatic `401` recovery is disabled so invalid credentials cannot start
   refresh.
6. Spring validates the credentials, sets the authentication cookies, and
   returns the current authorization context.
7. The frontend validates the response, clears staff-owned cache entries from
   the current tab, and stores the current account in SWR.

Frontend validation provides prompt feedback only. Spring repeats all security
validation and remains authoritative.

## 7. Tenant Registration

The signed-out surface provides Sign in and Register tenant tabs. Registration
collects the tenant name, first location, administrator display name, username,
required email, password, and frontend-only password confirmation.

Zod trims names, lowercases the username and email, validates the email, and
enforces the 12-character and 72-UTF-8-byte BCrypt password contract. On
success, the form clears both password values, switches to Sign in, prefills the
normalized username returned by Spring, and shows confirmation. It does not
mutate `/me`, authentication cookies, or staff-owned SWR state.

The shared request client still initializes and recovers CSRF for registration.
Automatic `401` recovery is disabled because registration is anonymous and must
never start a refresh attempt.

## 8. Protected Requests and Session Recovery

All staff request modules use `request()` or `apiFetch()` from the shared client
instead of calling `fetch` directly. JSON responses are validated by their Zod
schemas.

When a protected request returns `401`:

1. The client acquires the authentication-cookie lock.
2. It rechecks `GET /api/auth/me` while holding the lock.
3. If `/me` succeeds, another cooperating request or tab already refreshed the
   shared cookies, so the client does not rotate again.
4. If `/me` still returns `401`, the client posts
   `POST /api/auth/refresh` once.
5. After an existing or newly recovered session is available, the original
   request is retried once while the lock is still held.
6. A terminal `401` notifies `StaffPanel` in the current tab, clears staff-owned
   state, and shows login. There is no second refresh or request retry.

The refresh credential rotates and is single-use. All same-origin tabs in one
browser profile share the same refresh cookie. The browser Web Lock therefore
prevents cooperating tabs from presenting the same credential concurrently.
The `/me` recheck is what prevents a tab that waited for the lock from
unnecessarily rotating the replacement credential.

When Web Locks are unavailable, a small module-level queue provides equivalent
serialization only within the current tab. Cross-tab behavior is then
best-effort. The backend's credential-reuse detection remains the security
boundary and fails closed if unsupported or non-cooperating clients race.

No persistent browser marker is needed. If a refresh response is interrupted,
a later retry first checks `/me`: it reuses replacement cookies if the browser
received them, or lets the backend reject or recover the presented credential
otherwise. Network and server failures remain visible so the user can retry.

## 9. Logout

Ordinary logout:

1. initializes CSRF;
2. acquires the authentication-cookie lock;
3. posts `POST /api/auth/logout`;
4. if the access cookie expired first, performs at most one refresh and retries
   logout while retaining the lock;
5. lets Spring revoke the refresh session and clear the cookies;
6. clears the current account and staff-owned SWR cache entries in the current
   tab.

`logoutAll()` follows the same process with `POST /api/auth/logout-all`.
Other tabs reconcile on their next request, focus revalidation, or reconnect
revalidation. Immediate cross-tab logout animation or cache clearing is not a
requirement.

## 10. Authenticated Workspace and SWR Isolation

`/api/auth/me` is the frontend authentication gate. Staff-data cache keys begin
with `staff` and include the current account ID. Explicit login, logout, and a
terminal `401` purge staff entries in the current tab.

`OrderManagement` is also keyed by account ID when rendered. A different
account therefore receives a fresh component instance instead of inheriting the
previous account's draft label, selected order, or mutation state.

Operators render the Orders workspace directly. Administrators and managers
receive HeroUI Orders and Accounts tabs according to the capabilities returned
by `/api/auth/me`; visibility is presentation only. Administrators select an
accessible location and either Manager or Operator. Managers use their fixed
assignment and the fixed Operator role.

Order and account views use the same `staff/<accountId>/locations` SWR key, so
location reads are deduplicated and remain account-scoped. Successful
provisioning shows the created account summary, clears display name, username,
email, and password fields, and retains the selected location and role.

These measures prevent normal React and SWR carryover of locations, orders, QR
codes, and frontend-owned interaction state after the current tab observes an
account change. They complement backend authorization; they do not replace it.

## 11. Failure and Retry Rules

These rules are intentional:

* `401` may cause one lock-serialized refresh and one original-request retry.
* The lock holder checks `/me` before deciding to refresh.
* Authorization `403` never causes refresh.
* A recognized CSRF `403` may cause one CSRF bootstrap and one request retry.
* Login never starts automatic refresh.
* Refresh never recursively refreshes itself.
* A terminal `401` returns the current tab to login.
* Network and server failures remain retryable errors rather than being
  converted into authentication state.
* Automatically retried request bodies must be replayable. Current callers use
  JSON strings or no body; do not use this retry path with a consumed stream.

## 12. Invariants for Future Changes

Preserve all of the following:

* Never expose or store authentication credentials in JavaScript-managed
  storage.
* Treat one browser profile as one signed-in account.
* Do not promise different account identities in different tabs.
* Route staff requests through the shared API client.
* Use the same authentication-cookie lock for login, refresh, logout, and
  logout-all.
* Recheck `/me` after acquiring the lock and before refreshing.
* Keep refresh and original-request retries bounded to one each.
* Never interpret an authorization `403` as an expired session.
* Keep staff cache keys account-scoped and purge them when the current tab
  observes an authentication change.
* Reset frontend-owned state when the authenticated account changes.
* Keep backend authorization independent of frontend routing, capabilities, and
  visibility.
* Keep tenant registration outside the authentication-cookie lock and disable
  automatic session recovery for its request.
* Keep account-input Zod rules shared by registration and provisioning.
* Keep the location SWR key shared and account-scoped between Orders and
  Accounts.
* Keep `page.tsx` server-side unless the route itself genuinely needs client
  APIs.
* Update this document when the browser flow or its invariants change.

If the backend stops using a rotating single-use refresh credential or changes
its credential-reuse policy, review the lock design instead of assuming it is
still required.

## 13. Deferred Work

Other deferred work includes:

* a visible logout-all or session-management UI;
* account listing and status-management UI;
* additional administrator and location creation;
* invitations, email verification, recovery, and CAPTCHA;
* OIDC and external-identity linking;
* MFA, password recovery, passkeys, and improved initial-password delivery;
* PostgreSQL RLS and production operational hardening.

## 14. Verification After Changes

For a frontend-only authentication change, run:

```bash
npm --prefix apps/panel-app run check
git diff --check
```

If the Spring contract changes, statically validate every consumer and run the
relevant backend and frontend checks when practical. Use the full backend suite
when focused coverage is insufficient or explicitly requested.

When runtime verification is explicitly requested for session recovery, verify:

1. startup with and without a valid session;
2. successful and failed login;
3. simultaneous protected requests from two same-account tabs after access-token
   expiry;
4. logout in one tab being observed by another on its next request or focus;
5. authorization `403` not starting refresh;
6. a refresh network failure remaining bounded and recoverable through an
   explicit retry;
7. different accounts requiring separate profiles, private contexts, or
   devices.

When runtime verification is explicitly requested for registration or
provisioning, verify:

1. invalid email and password confirmation stay client-side;
2. successful registration returns to sign-in with the normalized username and
   does not authenticate automatically;
3. a registered administrator can provision managers and operators;
4. a manager can provision only operators for the assigned location;
5. an operator has no Accounts tab and direct unauthorized requests still fail;
6. conflict, rate-limit, CSRF-recovery, and API errors remain visible.

## 15. Browser References

* [Next.js Server and Client Components](https://nextjs.org/docs/app/getting-started/server-and-client-components)
* [Next.js `use client` directive](https://nextjs.org/docs/app/api-reference/directives/use-client)
* [MDN Web Locks API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API)
