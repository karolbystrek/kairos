# Kairos

Virtual pager system for restaurants: customers anonymously track QR-linked
orders, staff manage queues, and external systems use REST APIs and webhooks.
Repository: [https://github.com/karolbystrek/kairos](https://github.com/karolbystrek/kairos).
Use `gh` for all GitHub operations.

## Contents

- [Kairos](#kairos)
- [Contents](#contents)
- [Required workflow](#required-workflow)
- [Cloud resource approval](#cloud-resource-approval)
- [Repository and stack](#repository-and-stack)
- [Read when relevant](#read-when-relevant)

## Required workflow

Establish shared understanding with the user of the problem, scope, constraints,
and acceptance criteria before proposing an implementation plan. Scale planning
and review to the task's complexity and risk. Track substantial work in GitHub
issues; small documentation edits and mechanical cleanups do not require an
issue. Issues describe context, goals and acceptance criteria, not implementation
details. Discuss implementation choices when implementing the issue.

When working on a GitHub issue, set the conversation title to that issue's exact
title.

Create a new `<issue>-<topic>` branch from current `main` for tracked work, or a
descriptive branch for issue-exempt work. Validate, commit all task changes in
one Conventional Commit, and open a PR to `main`. Stop for user verification
and merge; never develop directly on `main` or merge the PR yourself. Keep
tracked task plans/progress in GitHub, not committed `.md` files. See
[the full workflow](docs/agents/issue-tracker.md) before changes.

Handle small tasks that can be completed quickly in the current session yourself.
Use subagents only for larger tasks with substantial, independent work where
parallel execution meaningfully reduces completion time. Avoid delegation when
coordination costs outweigh the benefit or changes overlap. Delegation alone
never requires a separate issue or branch.

## Cloud resource approval

Agents may use AWS CLI and Cloudflare CLI/API tools for read-only inspection.
Never modify AWS or Cloudflare resources without explicit user approval for
that action and scope, regardless of the tool or automation used. Credentials
and permission to implement code do not authorize live resource changes.
See [cloud operations](docs/agents/cloud-operations.md) before accessing either
service; existing explicit approval remains valid within its stated scope.

## Repository and stack

- `apps/customer-app/`: Next.js customer PWA, Serwist, IndexedDB, Web Push.
- `apps/panel-app/`: Next.js authenticated staff panel.
- Both frontends: Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3,
  Lucide React, Zod, native `fetch`, and SWR.
- `apps/api/`: Java 25, Spring Boot 4, Spring Security/MVC; PostgreSQL is
  authoritative, Redis Pub/Sub distributes customer events, ZITADEL owns identity.
- `compose.yaml`: shared Docker Compose topology with NGINX HTTPS ingress.
  Applications own their manifests and Dockerfiles.

## Read when relevant

- [Requirements topic map](docs/REQUIREMENTS.md): start with purpose/ownership
  and current delivery status, then read the sections relevant to the task.
  Follow security, persistence and contract links for every affected boundary;
  the topic files together form the canonical specification.
- [Architecture](docs/agents/architecture.md): before changes across application,
  API, security, persistence, or real-time boundaries.
- [Frontend conventions](docs/agents/frontend.md): before frontend changes;
  use its skill selection guidance for design, motion and React/Next.js work.
- [Backend conventions](docs/agents/backend.md): before API/Java changes.
- [Validation](docs/agents/validation.md): before selecting tests/checks and
  before declaring work complete.
- [Local development](docs/agents/local-development.md): before setup, migration,
  container, data, or runtime work; preserves user-owned containers/data.
- [Documentation maintenance](docs/agents/documentation.md): before documentation
  changes or recording accepted decisions. Keep agent guidance focused by purpose
  and update this index whenever it changes; keep task records in GitHub.
- [Issue workflow](docs/agents/issue-tracker.md): before planning or changing files,
  creating commits/PRs, or auditing issues.
- [Triage labels](docs/agents/triage-labels.md): when setting issue readiness.
- [Thesis framing](docs/PROBLEM_DESCRIPTION.md): when discussing thesis scope.
- [Authentication operations](docs/authentication-setup.md): before provider
  setup, credential rotation, or live authentication acceptance.

Kairos uses a fresh initial development schema until the user changes that policy:
edit the initial V1 migration when appropriate; no compatibility for local data
is required. This does not authorize container stops or data resets.

Keep all repository documentation Markdown under `docs/`, except the root
`AGENTS.md` and `README.md`. Deployment guides belong in `docs/deployment/`.
