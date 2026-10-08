# Documentation maintenance

## Contents

- [Documentation maintenance](#documentation-maintenance)
- [Contents](#contents)
- [Reading and canonical ownership](#reading-and-canonical-ownership)
- [File size and navigation](#file-size-and-navigation)
- [Durable knowledge and task records](#durable-knowledge-and-task-records)
- [Validation](#validation)

## Reading and canonical ownership

- Start at `docs/REQUIREMENTS.md` before planning, implementing or reviewing.
  Read purpose/ownership and delivery status, then the task-relevant topics and
  sections. Follow security, persistence and contract dependencies rather than
  requiring a full read of unrelated files. Surface conflicts explicitly.
- Record accepted product, architecture, security, ownership, scope and contract
  decisions in the canonical `docs/requirements/` topic file. Update the map
  when adding, moving or retiring a topic.
- Read `docs/PROBLEM_DESCRIPTION.md` for thesis framing. It describes thesis
  scope; requirements record current delivery and deferred work.
- Keep one canonical domain specification across applications. Link to it
  rather than duplicating rules in CONTEXT files, ADRs or task design documents.

## File size and navigation

- Store every repository documentation Markdown file under `docs/`, except
  root `AGENTS.md` and `README.md`. Deployment guides belong in
  `docs/deployment/`; executable deployment files remain in `deployment/`.

- Keep repository knowledge and rule Markdown files below 300 lines, including
  navigation and code examples; aim below 200 when practical. Short files do
  not need padding. Split by a coherent topic before exceeding 300 lines.
- Every file has a near-top `Contents` section with links to every heading,
  including the title, Contents and nested headings, in document order.
- Use descriptive headings that name the behavior, contract or procedure.
  Break long sections into meaningful subsections so a reader can locate a
  rule without reading the whole file. Keep complete examples/code fences
  together; do not split arbitrarily at a line limit.
- Keep `AGENTS.md` the concise entry point. Put durable agent guidance in
  focused `docs/agents/` files and link every file with when to read it.
  Requirements and operational guides use their own topic maps.
- When moving content, preserve its accepted meaning, update relative links
  and heading anchors, and leave an index at an established entry path.
- Use local heading links for quick navigation and cross-file links for
  dependencies. Add a task/topic map when an index routes readers to several
  files. Do not create a second canonical copy to preserve an old link.

## Durable knowledge and task records

- Store task discussions, designs, implementation plans, progress and audit
  reports in GitHub issues/sub-issues and PRs, not committed task `.md` files,
  including when a skill normally asks for a local plan or spec.
- Update documentation with the implementation it describes. Keep reusable
  setup documentation; remove obsolete or duplicate records after moving
  unfinished work to GitHub. Preserve accepted decisions in requirements.

## Validation

Check line counts, Contents coverage, heading anchors and relative links in
all affected files. When splitting a canonical document, compare its original
content with the new topic files to ensure no requirement or safety rule was
lost. Check references repository-wide and run `git diff --check`. Application
builds and live cloud actions are unnecessary for navigation-only edits.
