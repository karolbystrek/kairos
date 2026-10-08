# Failed release and maintenance

## Contents

- [Failed release and maintenance](#failed-release-and-maintenance)
- [Contents](#contents)
- [6. Failed release and maintenance](#6-failed-release-and-maintenance)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 6. Failed release and maintenance

A failure reports its stage and safe error metadata in Actions. The previous
success record remains, but the actual stack may be partially updated. Do not
interpret that record as the current state after a failure. Inspect the failed
stage and, only with runtime authorization, container health and bounded logs
privately. Redact credentials, bearer tracking references and personal data before
sharing diagnostics; avoid environment/config dumps and shell tracing.

Fix forward: correct configuration or commit a fix, obtain a new validated
release and approval, and rerun the normal command. There is no automated rollback.
A failed migration requires diagnosis, not removal of volumes, key regeneration,
checksum bypass or resetting production. Back up before destructive migrations
or risky data transformations; application redeployment cannot recover lost data.
At official launch explicitly change the fresh-development schema policy:
applied migrations become immutable and later changes use forward migrations.

| Maintenance | Procedure and verification |
| --- | --- |
| OS/Docker/infrastructure patches | Schedule an outage with the operator; back up when needed, review upstream changes, validate changed Compose/NGINX configuration and deploy a reviewed revision. Infrastructure tags can change independently of source; test upgrades deliberately and record pulled versions. Verify health and reboot recovery. |
| SSH/GHCR credentials | Install and verify replacement credentials through a trusted path before revoking old ones. Update the Environment SSH key/known_hosts after independently verifying any changed host key. Renew host registry credentials before expiry and verify all three pulls without exposing tokens. |
| Provider PATs/admin credentials | Follow [authentication operations](../authentication-setup.md#operations-and-limits). Inventory existing token expirations; bootstrap preserves them. New API PATs use `9999-12-31T23:59:59Z` with the existing service roles; replace the API volume file atomically, restart API, verify auth and revoke the old PAT. The bootstrap administrator PAT is separate and never mounted into the API; rotate it through trusted provider administration. |
| Origin CA certificate | Track expiry with `openssl x509 -in /srv/kairos/tls/tls.crt -noout -dates`. Provision a matching replacement covering all names into the external TLS directory, preserve permissions, recreate/reload NGINX in an approved window, verify Full (strict) paths, then retire the old certificate/key. Never disable strict TLS to hide an expiry failure. |
| Encryption/VAPID keys | Do not overwrite keys in place as a routine rotation. Current encrypted webhook signing secrets and push subscriptions require their original keys; there is no multi-key decrypt/re-encrypt command. Plan a separately reviewed data transformation or explicit retirement of affected data/deliveries, with backup and retention implications. VAPID changes require browser subscription replacement and existing delivery acceptance. Preserve the ZITADEL master key with its database. |
| Security-event retention | Set operational ownership and a deliberate retention policy before launch for invitations, provider/session records, logs and protected backups. Current invitation metadata is retained indefinitely; do not invent a cleanup deadline or delete history without an accepted data-retention change. |

Backups and alerts are [step six](https://github.com/karolbystrek/kairos/issues/18):
confirm daily encrypted off-VM backups, 30-day retention and a 24-hour data-loss
target with the owner; include both databases/roles, keys, provider credential
volumes and protected configuration/certificate material. Rehearse restore on a
replacement host and establish external availability, disk/memory, backup-failure
and certificate-expiry alerts with a named responder and patch schedule. Those
operational targets and checks are pending, not provided by this runbook.

Public launch also requires the independent RLS privilege/isolation work, live
pinned-ZITADEL verification, browser/PWA/push device acceptance and hosted
TLS/firewall/throttling/REST/SSE checks in step seven. Record each as passed,
failed, blocked or not run; documentation and mocked tests do not satisfy them.
