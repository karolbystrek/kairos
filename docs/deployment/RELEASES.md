# First install and later releases

## Contents

- [First install and later releases](#first-install-and-later-releases)
- [Contents](#contents)
- [5. First install and later releases](#5-first-install-and-later-releases)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 5. First install and later releases

After provisioning and authorization, use a successful main push run or a manual
main dispatch (`gh workflow run ci.yml --ref main`). The workflow validates,
publishes a complete `release-manifest` artifact, then waits for owner approval.
Review the run's full revision, all three digests, launch blockers and maintenance
window before approval. Retain the artifact/run ID as release evidence.

Actions verifies the bundled manifest against the run revision, uploads through
a private incoming directory, activates `/srv/kairos/releases/<revision>`, then
invokes the command below with absolute paths. A retry of an existing revision
requires an identical archive. For an authorized manual invocation, use that
same verified, extracted artifact, not files from the moving main checkout:

```bash
bash "/srv/kairos/releases/${KAIROS_REVISION}/deployment/deploy.sh" \
  "/srv/kairos/releases/${KAIROS_REVISION}" /srv/kairos/production.env
```

The command validates inputs/Compose and external read-only secrets, writes a
release-owned `compose.images.json` override, pulls images, then waits in order
for PostgreSQL/Redis, ZITADEL, a successfully recreated one-shot bootstrap, API,
frontends and recreated NGINX. Bootstrap initializes missing provider credentials
and preserves existing ones. Never start the API first with `--no-deps` on an
empty host. No setup or key generation is part of deployment.

Readiness stages allow 300 seconds each, bootstrap execution 360 seconds and
pulls 900 seconds. The stable project name preserves state; the host lock rejects
concurrent deployments in addition to Actions concurrency. HTTPS probes through
Cloudflare require `200` from both frontend roots and `/api/auth/v1/csrf` with a
nonempty application token. The command writes `deployed-release.json` atomically
only after every check passes. These probes do not prove SSE, RLS, PWA or complete
authentication acceptance. Record first install, reboot recovery and update
acceptance separately in [the launch work](https://github.com/karolbystrek/kairos/issues/19).
Docker and containers with `unless-stopped` must recover on reboot; health checks
alone do not restart unhealthy running processes.
