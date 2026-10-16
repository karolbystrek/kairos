# Staff authentication operations

## Contents

- [Staff authentication operations](#staff-authentication-operations)
- [Contents](#contents)
- [Setup](#setup)
- [Sessions and account flows](#sessions-and-account-flows)
- [Operations and limits](#operations-and-limits)

The API stores staff credentials itself; see
[staff credentials](requirements/staff-credentials.md) for the design. There is
no identity provider to start, bootstrap or configure.

## Setup

Run `./setup.sh`, then start Compose. Setup creates the application encryption
keys and optional local TLS material; staff credentials need no extra secret.
The `V3__store_staff_credentials` migration creates the credential table on API
startup.

## Sessions and account flows

For local development, set `EMAIL_VERIFICATION_ENABLED=false` in `.env` (as in
`.env.example`) and restart the API. Registration and unverified sign-in then
mark the email verified and complete onboarding without sending a code. The API
defaults to `true`; keep verification enabled for production. Re-enabling it
does not undo emails already marked verified.

Users register and log in on the Kairos panel with email/password. Registration
sends a 6-digit verification code before creating a tenant/admin. Confirming
ownership opens the existing first-location onboarding flow. Codes expire after
one hour, permit five wrong attempts and can be resent after 60 seconds.
Unverified sign-in without an Account resumes this step; a new registration can
replace an abandoned unverified identity without an Account. Member registration
consumes an emailed invitation bound to its recipient and marks that email
verified without a separate code step. A forgotten password is reset from the
login page with an emailed code under the same rules, which also ends every
existing session. Social login and MFA remain deferred.

Kairos sends Polish mail through Resend from
`Kairos <no-reply@kairos.karolbystrek.pl>` with no Reply-To or open/click tracking.
Set `RESEND_API_KEY` in protected production configuration and preserve the
bootstrap-generated `email-encryption.bin`. Production refuses startup without
the API key; local/test mode logs delivery metadata without codes or message
bodies and sends no email. See
[transactional email setup](deployment/CONFIGURATION.md#transactional-email-resend) and
[key maintenance](deployment/MAINTENANCE.md).
Signed-in users can change a password using the current password; this signs out
all sessions and requires a new login.

Spring Session JDBC stores sessions in the Kairos database. The host-only,
Secure, HttpOnly `__Host-session` cookie is an opaque ID. Protected requests
check the session cutoff and current local permissions and renew 30-day idle
expiry and cookie retention. There is no absolute lifetime for active users.
Background authenticated requests also count as activity. No browser JWT,
refresh endpoint, custom tab coordination or authentication response-body tokens
are used. Ordinary logout revokes the current browser; a password change revokes
all Kairos sessions.

## Operations and limits

Password hashes, verification-code hashes and session IDs live in PostgreSQL;
protect the database and its backups as credential-bearing data. Changing
database passwords in `.env` alone does not rotate existing PostgreSQL roles.
Deleting an account's data through the retention purge also deletes its
credentials.
