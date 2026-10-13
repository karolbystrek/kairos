# Legal readiness

## Contents

- [Legal readiness](#legal-readiness)
- [Contents](#contents)
- [Status and scope](#status-and-scope)
- [Roles and documents](#roles-and-documents)
- [Data inventory](#data-inventory)
- [Cookies and device storage](#cookies-and-device-storage)
- [Google review invitations](#google-review-invitations)
- [Retention commitments](#retention-commitments)
- [Launch blockers](#launch-blockers)
- [Open questions for legal review](#open-questions-for-legal-review)

## Status and scope

This records the assessment for
[issue #71](https://github.com/karolbystrek/kairos/issues/71). It is an
engineering assessment, not legal advice; the drafts need review by a lawyer
before real users. Kairos is offered free of charge as a bachelor's thesis
project for a fixed test period. Neither the absence of payment nor the
non-commercial purpose exempts it from GDPR or the Polish Act on Providing
Services by Electronic Means (UŚUDE), which requires published terms for any
electronic service.

## Roles and documents

| Document | Route | Audience |
| --- | --- | --- |
| Regulamin | panel `/regulamin` | Restaurants and staff |
| Polityka prywatności | panel `/polityka-prywatnosci` | Staff and landing visitors |
| Prywatność | customer `/prywatnosc` | Restaurant customers |

- The operator (Karol Bystrek) is the **controller** of staff account,
  invitation, session and contact data.
- For customer order-tracking data each restaurant is the **controller** and
  the operator is its **processor**. The Art. 28 GDPR processing terms are
  § 8 of the Regulamin rather than a separate agreement.
- Operator details live in one constant per app
  (`apps/*/src/legal/operator.ts`).
- Entry points: landing footer, a required terms checkbox on public and
  invitation registration, the customer home screen and the notification
  prompt. Terms acceptance is enforced in the browser only; no acceptance
  record is stored server-side.

## Data inventory

| Data | Subjects | Purpose | Basis |
| --- | --- | --- | --- |
| Email, password hash (ZITADEL), role, assignments, status | Staff | Account and service | Art. 6(1)(b) |
| Invitation email, role, location | Invited staff | Team onboarding | Art. 6(1)(b)/(f) |
| Session and CSRF cookies, IP, security events | Staff | Sign-in and abuse protection | Art. 6(1)(b)/(f) |
| Tracking reference, order number and label, status | Customers | Order tracking (restaurant's purpose) | Processor |
| Encrypted push subscription and enrollments | Customers who opt in | Status notifications | Processor |
| Anonymous tenant/location/hour order counts and duration summaries | No user identities | Restaurant business measurements | Engineering data inventory; legal review remains required |
| Approximate tenant/location/hour panel and review action counts | No user identities in aggregates | Feature usage | Engineering data inventory; legal review remains required |
| IP addresses in gateway traffic | Everyone | Delivery and protection | Art. 6(1)(f) / processor |

Recipients: AWS Lightsail in `eu-central-1` (hosting) and Cloudflare (proxy,
possible US transfer under the EU-US Data Privacy Framework or SCCs). Browser
push services carry encrypted payloads selected by the customer's browser.
ZITADEL is self-hosted on the same VM. Resend processes staff recipient email
addresses and transactional verification messages; provider terms, retention
and international-transfer arrangements require legal review before real users. Server-side [anonymous order
analytics](order-analytics.md) contain business counts and durations without user,
order or tracking identities. Separate [usage counts](usage-analytics.md) collect predefined browser actions
without identifying people, sessions or devices in aggregates. There is
no advertising, profiling or automated decision-making. Transactional email
uses no open or click tracking.

## Cookies and device storage

No consent banner is needed. Under art. 399 of the Polish Electronic
Communications Law (PKE), storage that is strictly necessary for a service the
user requested needs information but not consent. All current storage
qualifies:

- panel session and CSRF cookies and the theme preference;
- customer IndexedDB order snapshots, notification and review-dismissal state,
  local preferences, and the service worker for offline use and push.

Push notifications are opt-in through the in-app prompt and browser
permission, and can be withdrawn with the bell control or browser settings.
Browser analytics and review metrics need separate legal/consent assessment
under #71 before real-user collection and must not run before consent where
consent is required. Their implementation is not a legal assessment.

## Google review invitations

The delayed review push was removed. A push sent to promote a Google review is
arguably direct marketing under art. 398 PKE, and browser notification
permission is not consent for it. The invitation is now shown only as an
in-app prompt while the customer uses the app, which is not a message sent to
them. The trade-off is that customers who close the app never see it.

## Retention commitments

- Push subscriptions are removed 30 days after their last enrollment or on
  opt-out; delivery rows are kept for 7–30 days (see
  [resilience](resilience.md)).
- Account and Location Delete block access immediately. Thirty days later the
  [retention purge](resilience.md#retention-purge) deletes the account with its
  ZITADEL identity and invitations, or the location with its orders and push
  data. Until then an archived account's email stays reserved.
- Abandoned registrations, with their unverified ZITADEL identity, are
  deleted 30 days after the last code; finished email delivery records are
  deleted 30 days after completion once nothing references them.
- Security events: invitations are deleted 30 days after they end, staff
  sessions expire after 30 idle days, and production logs rotate at three
  10 MB files per service.
- Anonymous business aggregates and pending inputs survive location purge; aggregates are retained indefinitely until permanent tenant deletion erases both. The test-period deletion commitment still applies to the tenant.
- Other orders and accounts are kept until the end of the test period. The
  notices commit to deleting all data within 30 days after the test period
  ends, or within 30 days of a restaurant's deletion request (Regulamin § 5 and
  § 8); deleting a whole restaurant or an administrator account is a manual
  operator action.
- No backups are taken; the Regulamin states that data may be lost.

## Launch blockers

- Replace the placeholders in `apps/*/src/legal/operator.ts`: correspondence
  address, effective date and test-period end date.
- Have a lawyer review the three documents and the open questions below.
- Delete all data within 30 days after the test period ends, and a whole
  restaurant's data within 30 days of its request, as promised (manual).

## Open questions for legal review

- Whether the operator is a joint or independent controller for push
  subscriptions, since one browser subscription can span orders from several
  restaurants.
- Whether ZITADEL's event store, which keeps events of deleted users, needs
  its own retention limit.
- Whether a browser-only terms checkbox is sufficient evidence of acceptance,
  or acceptance should be recorded server-side.
- Confirm the cited PKE provisions (art. 398 and art. 399 ust. 3) against
  the final text of the act.
- Whether charging restaurants later would require a registered business and
  revised terms.
