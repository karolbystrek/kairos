# Kairos

Virtual pager system for restaurants: customers anonymously track QR-linked
orders, staff manage queues, and external systems use REST APIs and webhooks.
Repository: [https://github.com/karolbystrek/kairos](https://github.com/karolbystrek/kairos).
Use `gh` for all GitHub operations.

## Workflow

Every task moves through the stages below, from idea to shipped PR. Use the
installed skills that fit each stage (brainstorming, planning, debugging,
test-driven development, review, PR writing and so on); this file sets the
stages and rules, not the tools. Scale each stage to the task's size and risk.

1. **Understand.** Read the relevant requirements and source first. Trivial
   tasks need no discussion. For non-trivial tasks, confirm the goal and agree
   on an approach with the user before changing files. For large or risky
   tasks, discuss scope, constraints and acceptance criteria, offer a few
   options with one recommendation and its reason, and resolve open questions
   with the user rather than choosing silently.
2. **Track.** Substantial work (features, notable bugs, multi-step work) gets a
   GitHub issue, reusing an existing one when it fits. Issues state context,
   goal and acceptance criteria, not implementation details; split large work
   into sub-issues. Small fixes go straight to a PR. Keep plans and progress in
   issue and PR comments, never in committed task `.md` files. When working on
   an issue, set the conversation title to the issue's exact title.
3. **Branch.** Check `git status --short` and leave unrelated changes alone.
   Branch from current `origin/main` as `<issue>-<topic>`, or with a
   descriptive name when there is no issue. Never work on `main`.
4. **Build.** Implement only the agreed scope, following existing patterns. For
   bug fixes and logic changes, write a failing test first when practical.
   Update documentation and requirements in the same change as the behavior
   they describe. Split work across parallel agents only when it is large, the
   pieces touch different files, and parallelism clearly saves time.
5. **Verify.** Run focused checks for the affected scope plus `git diff --check`;
   leave full suites to CI unless the user asks. Self-review the diff. For large
   or cross-module changes, also get an independent review, address its
   findings and rerun the affected checks.
6. **Ship.** Use Conventional Commit messages; PRs are squash-merged, so the PR
   title is a Conventional Commit too. Open a PR to `main` describing the
   outcome and the checks run, link the issue with a closing keyword, get CI
   green, and stop. The user verifies,
   merges and decides when to deploy; never merge or deploy yourself.

## Cloud resource approval

Agents may use AWS CLI and Cloudflare CLI/API tools for read-only inspection.
Never modify AWS or Cloudflare resources without explicit user approval for
that action and scope, regardless of the tool or automation used. Credentials
and permission to implement code do not authorize live resource changes.
See [cloud operations](docs/agents/cloud-operations.md) before accessing either
service; existing explicit approval remains valid within its stated scope.

## Repository and stack

- PostgreSQL is authoritative, Redis Pub/Sub distributes customer events,
  the API owns staff credentials and sessions.
- `compose.yaml`: shared infrastructure (PostgreSQL, Redis) on loopback ports;
  `compose.production.yaml` adds the application images and NGINX HTTPS ingress.
  `compose.local.yaml` adds the complete Docker development stack with hot reload.
  Applications own their manifests and Dockerfiles; the two frontends share
  `apps/panel-app/Dockerfile`.

## Local development

- `./setup.sh --dev`: first run; prepares config and keys, starts all five
  services in Docker with hot reload. `--prod` selects production instead.
- `docker compose up --build -d`: start or rebuild the selected mode.
- `docker compose stop`: stop and keep data; `docker compose logs -f`: follow logs.
- `./reset.sh`: delete containers, volumes and keys; refuses production; run only
  when the user asks.

Details: [local development](docs/agents/local-development.md).

## Read when relevant

- [Requirements topic map](docs/REQUIREMENTS.md): start with purpose/ownership
  and current delivery status, then read the sections relevant to the task.
  Follow security, persistence and contract links for every affected boundary;
  the topic files together form the canonical specification.
- [Architecture](docs/agents/architecture.md): before changes across application,
  API, security, persistence, or real-time boundaries.
- [Frontend conventions](docs/agents/frontend.md): before frontend changes.
- [Backend conventions](docs/agents/backend.md): before API/Java changes.
- [Validation](docs/agents/validation.md): before selecting tests/checks and
  before declaring work complete.
- [Local development](docs/agents/local-development.md): before setup, migration,
  container, data, or runtime work; preserves user-owned containers/data.
- [Brand mark](docs/brand/README.md): before changing logos, icons, favicons
  or share-preview images.
- [Documentation maintenance](docs/agents/documentation.md): before documentation
  changes or recording accepted decisions. Keep agent guidance focused by purpose
  and update this index whenever it changes; keep task records in GitHub.
- [GitHub issues](docs/agents/issue-tracker.md): before creating, linking
  or auditing issues.
- [Thesis framing](docs/PROBLEM_DESCRIPTION.md): when discussing thesis scope.
