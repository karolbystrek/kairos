# First install and later releases

## Contents

- [First install and later releases](#first-install-and-later-releases)
- [Contents](#contents)
- [5. First install and later releases](#5-first-install-and-later-releases)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 5. First install and later releases

CI validates pull requests and main pushes without building images. After
provisioning and authorization, start the deployment workflow from `main`
(the default branch; other refs are refused):

```bash
gh workflow run deploy.yml --ref main
```

Deployment always uses the latest `main` revision captured when the workflow
is dispatched; there is no revision input, and dispatching is the production
approval. It runs the full CI workflow for that revision, then builds and pushes
all three images to GHCR. An image that already exists for the revision is reused
unchanged, so retries keep the same digests. The `Publish release` job inspects
the images and creates the `release-manifest` artifact, regenerating a
byte-identical bundle for an existing revision. Changing runtime files cannot
replace an existing host release under that revision; use a new revision. The
deployment revision stays fixed even if `main` advances during the run. Retain
the workflow run ID as release evidence. Deployments are serialized and do not
cancel an active deployment.

Actions verifies the bundled manifest against the selected revision, uploads through
a private incoming directory, activates `/srv/kairos/releases/<revision>`, then
invokes the command below with absolute paths. A retry of an existing revision
requires an identical archive. For an authorized manual invocation, use that
same verified, extracted artifact, not files from the moving main checkout:

```bash
python3 "/srv/kairos/releases/${KAIROS_REVISION}/deployment/deploy.py" \
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
only after every check passes. These probes do not prove SSE, RLS, PWA, email
delivery or complete authentication acceptance; verify email with the
[transactional email check](CONFIGURATION.md#transactional-email-resend). Record first install, reboot recovery and update
acceptance separately in [the launch work](https://github.com/karolbystrek/kairos/issues/19).
Docker and containers with `unless-stopped` must recover on reboot; health checks
alone do not restart unhealthy running processes.
