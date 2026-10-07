# Documentation maintenance

- Read `docs/REQUIREMENTS.md` in full before planning, implementing, reviewing,
  or changing code, migrations, APIs, security, infrastructure, or tests. Use its
  terminology and record accepted product, architecture, security, ownership,
  scope, and contract decisions there. Surface conflicts explicitly.
- Read `docs/PROBLEM_DESCRIPTION.md` for thesis framing. It describes the thesis
  scope; requirements record current delivery and deferred work.
- Keep `AGENTS.md` a concise entry point. Put durable agent guidance in focused
  `docs/agents/` files, one area/purpose per file, and link every such file from
  `AGENTS.md` with when to read it. Update those links when files move or disappear.
- Keep one canonical domain specification across applications. Do not duplicate
  it in CONTEXT files, ADRs, or task-specific design documents.
- Store task discussions, designs, implementation plans, progress, and audit
  reports in GitHub issues/sub-issues and PRs, not committed task `.md` files,
  including when a skill normally asks for a local plan or spec.
- Update documentation with the implementation it describes. Keep reusable
  setup documentation; remove obsolete or duplicate records after moving
  any unfinished work to GitHub. Preserve accepted decisions in requirements.
