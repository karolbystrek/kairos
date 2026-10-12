# First install and later releases

## Contents

- [First install and later releases](#first-install-and-later-releases)
- [Contents](#contents)
- [5. First install and later releases](#5-first-install-and-later-releases)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 5. First install and later releases

CI validates main pushes and builds all three application images without
publishing to GHCR or requesting production approval. Main CI image artifacts
are retained for 30 days. After provisioning and authorization, start the
separate deployment workflow from `main`:

```bash
gh workflow run deploy.yml --ref main
```

Deployment always uses the latest `main` revision captured when the workflow
is dispatched; there is no revision input. Its `Publish release` job locates a
successful main CI run for that exact revision with all three unexpired image
artifacts, publishes those exact images, and creates the `release-manifest`
artifact. Missing or expired images fail publication before production approval;
finish or rerun CI for the revision, then retry deployment. Publication reuses
existing immutable registry images and regenerates a byte-identical bundle from
the same image digests and runtime contents. Rebuilding images with changed
digests or changing runtime files cannot replace an existing host release under
that revision; use a new revision. The deployment revision stays fixed even if
`main` advances while approval is pending.

Review the selected CI run, full revision, all three digests, launch blockers and
maintenance window before approving `Deploy production`. Retain both workflow
run IDs as release evidence. New main pushes create no deployment approval waits;
actual deployments remain serialized and do not cancel an active deployment.

Actions verifies the bundled manifest against the selected revision, uploads through
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
for PostgreSQL/Redis, ZITADEL, the recreated provider bootstrap, API, frontends
and recreated NGINX. Fresh PostgreSQL startup initializes its restricted roles
through the standard entrypoint; Spring Flyway applies V1 automatically. Existing
volumes retain their schema and roles; see [database initialization](DATABASE.md).
Provider bootstrap initializes missing credentials
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
