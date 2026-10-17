# Failed release and maintenance

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## Failed release and maintenance

A failure reports its stage and safe error metadata in Actions. The previous
success record remains, but the actual stack may be partially updated. Do not
interpret that record as the current state after a failure. Inspect the failed
stage and, only with runtime authorization, container health and bounded logs
privately. Redact credentials, bearer tracking references and personal data before
sharing diagnostics; avoid environment/config dumps and shell tracing.

When a release fails at any stage, including the public probes, the deployment
command automatically redeploys the previous release recorded in
`deployed-release.json` (if present) and still exits non-zero. The rollback
uses that record's original runtime directory, digest-pinned images and private
environment snapshot, then runs the same checks; its outcome
is printed to the log. Rollback does not undo database migrations, so every
migration must stay compatible with the previous release (expand/contract:
add first, remove only in a later release once no running release uses it).

`deployed-release.json` atomically records the successful release directory and
`/srv/kairos/environments/*.env` snapshot, plus the preceding successful record
in `previous`. Snapshot files use `0600` within a `0700` directory. Changing
`production.env` for the next attempt cannot overwrite recovery configuration.
Retries keep the preceding distinct successful revision. Legacy records containing
only revision/images cannot restore configuration: the first successful deployment
with this command establishes the full recovery point. Keep external keys and TLS
files at their recorded paths; snapshots retain references, not copies of those
files. Replacing or revoking those external files/credentials can invalidate rollback.

A queued deployment checks the latest manual `main` dispatch using `gh` before
obtaining AWS credentials. A newer dispatch supersedes older runs even if it
fails or is cancelled; dispatch again deliberately to retry. The check fails
closed when GitHub cannot verify the latest run. An already active deployment
finishes under the existing production concurrency lock.

Then fix forward: correct configuration or commit a fix, obtain a new validated
release and approval, and rerun the normal command.
A failed migration requires diagnosis, not removal of volumes, key regeneration,
checksum bypass or resetting production. Application redeployment cannot recover
lost data. The current development VPS accepts that loss under the
[operations policy](../requirements/routing-releases.md#development-operations-policy).

| Maintenance | Procedure and verification |
| --- | --- |
| OS/Docker/infrastructure patches | Schedule an outage with the operator; review upstream changes, validate changed Compose/NGINX configuration and deploy a reviewed revision. Infrastructure tags can change independently of source; test upgrades deliberately and record pulled versions. Verify health and reboot recovery. |
| SSH/GHCR credentials | Install and verify replacement credentials through a trusted path before revoking old ones. Update the Environment SSH key/known_hosts after independently verifying any changed host key. Renew host registry credentials before expiry and verify all three pulls without exposing tokens. |
| Staff credentials | Password hashes, verification-code hashes and session IDs live in PostgreSQL; protect the database and its backups as credential-bearing data. The retention purge deletes an account's credentials with its data. There is no staff service token to rotate. Changing database passwords in `.env` alone does not rotate existing PostgreSQL roles. |
| Resend sending key | Replace the `RESEND_API_KEY` production Environment secret, deploy in an approved window and verify bounded delivery outcome metadata before revoking the old key. Never log codes, invitation tokens or email bodies. Queued messages survive API restarts; terminal delivery clears their encrypted contents. |
| Origin CA certificate | Track expiry with `openssl x509 -in /srv/kairos/tls/tls.crt -noout -dates`. Provision a matching replacement covering all names into the external TLS directory, preserve permissions, recreate/reload NGINX in an approved window, verify Full (strict) paths, then retire the old certificate/key. Never disable strict TLS to hide an expiry failure. |
| Encryption/VAPID keys | Do not overwrite keys in place as a routine rotation. Current encrypted webhook signing secrets, push subscriptions and pending email contents require their original keys; there is no multi-key decrypt/re-encrypt command. Plan a separately reviewed data transformation or explicit retirement of affected data/deliveries, with backup and retention implications. VAPID changes require browser subscription replacement and existing delivery acceptance. |
| Security-event retention | The API purges finished invitations 30 days after they end and deleted accounts/locations after the 30-day grace period ([retention purge](../requirements/resilience.md#retention-purge)); staff sessions expire after 30 idle days and container logs rotate at 3 × 10 MB per service. Deleting a whole tenant or an administrator account on request is manual. Protected-backup retention still needs an owner before launch. |

## Bounded release and image cleanup

Cleanup needs explicit operator authorization and an idle deployment window.
First verify the latest deployment attempt succeeded; after any failed deployment
or rollback, resolve the stack state before deleting runtime files or images.
Hold the same host lock for the entire inspection/removal session:

```sh
flock -n /srv/kairos/deploy.lock bash
```

Inside that shell, privately inspect only the recovery identities (never print
snapshot contents):

```sh
python3 - <<'PYTHON'
import json
from pathlib import Path
record = json.loads(Path('/srv/kairos/deployed-release.json').read_text())
for release in (record, record.get('previous')):
    if release:
        print('KEEP runtime:', release['release_directory'])
        print('KEEP environment:', release['env_file'])
        for image in release['images'].values():
            print('KEEP image:', image)
PYTHON
find /srv/kairos/releases -mindepth 1 -maxdepth 1 -type d -print
find /srv/kairos/environments -mindepth 1 -maxdepth 1 -type f -name '*.env' -print
docker image ls --no-trunc --digests 'ghcr.io/karolbystrek/kairos/*'
docker ps -a --no-trunc --format '{{.Image}} {{.ID}} {{.Names}}'
```

Stop if either retained runtime/snapshot is absent or metadata is incomplete.
For each KEEP image, run `docker image inspect --format '{{.Id}}' DIGEST_REFERENCE`
and preserve that image ID, all its tags, and images used by any container.
Keep PostgreSQL, Redis and NGINX images too. Review candidates individually:
only obsolete Kairos application images and release directories outside both
recovery points may be removed. Remove each reviewed absolute release directory
with `rm -r -- /srv/kairos/releases/EXACT_OBSOLETE_REVISION`, each reviewed obsolete
snapshot with `rm -- /srv/kairos/environments/EXACT_OBSOLETE_FILE.env`, and each
reviewed image reference/ID with `docker image rm EXACT_OBSOLETE_REFERENCE`.
Do not use force, wildcard deletion, image/system/container/volume prune, Compose
down, or any volume command. If Docker refuses an image removal, investigate;
never force it. Failed-attempt snapshots and staging directories may also be
removed individually once no deployment is active and both recovery points are
protected. Exit the shell to release the lock. Reinspect both retained runtime
and environment paths and their image IDs afterward. This procedure never
removes database, Redis or application volumes.

[Step six](https://github.com/karolbystrek/kairos/issues/18) prepares native Lightsail
email alarms for status-check failures, sustained high CPU and low CPU burst
capacity. Follow [alarm setup](MONITORING.md) for provisioning, notification tests
and response guidance. Live alarms and email delivery were verified on 2026-10-08. Disk,
memory and public HTTPS alerts are deferred; no custom host monitoring runs.
Backups, retention/recovery targets and restore rehearsals are also deferred
until real users or valuable data; confirm those targets at that stage.

Public launch also requires the independent RLS privilege/isolation work, live
authentication verification, browser/PWA/push device acceptance and hosted
TLS/firewall/throttling/REST/SSE checks after step 6. Record each as passed,
failed, blocked or not run; documentation and mocked tests do not satisfy them.
