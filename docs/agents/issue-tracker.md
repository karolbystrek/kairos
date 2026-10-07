# Issue-first development workflow

Repository: [karolbystrek/kairos](https://github.com/karolbystrek/kairos).
Use `gh` for every GitHub operation; use `--body-file` for multiline content.

1. Read the relevant guidance and source, then establish shared understanding
   with the user of the problem, scope, and implementation plan. Before changing
   repository files, save that agreed plan as a new GitHub issue with acceptance
   criteria and validation. For an existing issue, record the agreed plan there
   rather than duplicating it.
2. Split a plan with distinct implementation steps into native GitHub sub-issues
   linked to its parent. Give each implementation issue a focused, independently
   reviewable scope; keep plans and progress in GitHub rather than task Markdown.
3. After recording the plan, inspect `git status --short`, preserve unrelated
   work, and create a new `codex/<issue>-<topic>` branch from current `main`
   (normally `origin/main`). All repository changes happen on branches, never
   directly on `main`. Each implementation sub-issue follows this same cycle;
   coordinate dependencies through reviewed/merged PRs.
4. Implement only the issue's agreed scope, update durable documentation, and run
   the affected checks plus `git diff --check`. Commit all task changes in one
   Conventional Commit, excluding unrelated changes and secrets. Split large
   work into issues before implementation rather than multiple commits.
5. Push the branch and create a PR targeting `main`, linking the implementation
   issue and its parent, describing the outcome and checks. Use closing keywords
   for completed implementation issues; close a parent only when all its scope
   is complete. Mark the issue `ready-for-human` and stop development for user
   verification. The user decides whether to merge; agents do not merge or
   continue with another implementation issue without user instruction.

Use `gh issue create/view/list/edit/comment/close`, `gh pr create`, and
`gh api repos/karolbystrek/kairos/issues/<parent>/sub_issues -X POST
-F sub_issue_id=<child-database-id>` for native parent/child links. Obtain the
child database ID with `gh api repos/karolbystrek/kairos/issues/<child> --jq .id`.

During issue audits, compare scope with current source and history. Leave
unfinished issues open. Close previously implemented issues with a comment
explaining the evidence and linking the implementing PR or commit; mention
later accepted replacements rather than promising superseded behavior.
