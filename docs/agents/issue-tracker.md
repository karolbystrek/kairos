# Task development workflow

## Contents

- [Task development workflow](#task-development-workflow)
- [Contents](#contents)
- [Issue and implementation cycle](#issue-and-implementation-cycle)
- [GitHub commands and sub-issues](#github-commands-and-sub-issues)
- [Issue audits](#issue-audits)

Repository: [karolbystrek/kairos](https://github.com/karolbystrek/kairos).
Use `gh` for every GitHub operation; use `--body-file` for multiline content.
Use descriptive issue titles without an agent prefix.

## Issue and implementation cycle

1. Invoke the `/grilling` skill before starting the task discussion with the
   user. Read the relevant guidance and source, then establish shared understanding
   with the user of the problem, scope, constraints and acceptance criteria
   before proposing an implementation plan. Discuss the plan and implementation
   details with the user until both reach shared understanding, before starting
   implementation. For each area of the task, propose a short list of possible
   solutions, identify one recommended solution with justification, and resolve
   questions and trade-offs with the user. Resolve material ambiguity first;
   keep simple tasks brief and expand planning only for complexity or risk.
2. Create an issue for substantial work that benefits from durable tracking.
   Small documentation edits and mechanical cleanups do not require an issue;
   keep their scope and validation in the conversation and resulting PR.
   Reuse an existing issue when it already covers the task. Issue descriptions
   explain the context, goal and observable acceptance criteria; do not include
   implementation details such as class names, file-by-file edits or algorithms.
   Discuss and agree implementation choices when implementing the issue, and
   record tracked plans and progress in issue comments rather than task Markdown.
3. Split substantial, independently reviewable scopes into native GitHub
   sub-issues linked to their parent. Handle quick tasks in the current session
   yourself. Use subagents only for larger tasks with substantial independent
   work, non-overlapping changes and a meaningful benefit from parallel execution.
   Delegation alone does not require another issue or branch; small work within
   a larger delegated scope shares its implementation issue and branch.
4. Inspect `git status --short`, preserve unrelated work, and create a new
   `<issue>-<topic>` branch from current `main` (normally `origin/main`), or a
   descriptive branch for issue-exempt work. Never change files directly on
   `main`. Each implementation sub-issue follows this same cycle; coordinate
   dependencies through reviewed/merged PRs.
5. Implement only the agreed scope, reuse existing patterns, and avoid unrelated
   refactoring or speculative abstractions. Update durable documentation and run
   checks appropriate to the affected behavior and risk plus `git diff --check`.
   Revisit the plan only when new evidence or scope changes require it. Report
   checks as passed, failed, blocked or not run, including material limitations.
   Commit all task changes in one Conventional Commit, excluding unrelated
   changes and secrets. Split large work into issues before implementation.
6. Push the branch and create a PR targeting `main`, describing the outcome and
   checks. Link the implementation issue and parent when applicable, using
   closing keywords for completed implementation issues; close a parent only
   when all its scope is complete. Mark tracked issues `ready-for-human` and stop
   for user verification. The user decides whether to merge; agents do not merge
   or continue with another implementation issue without user instruction.
   Deployment is a separate action: code approval does not authorize live cloud
   changes. Follow [cloud resource approval](cloud-operations.md).

## GitHub commands and sub-issues

Use `gh issue create/view/list/edit/comment/close`, `gh pr create`, and
`gh api repos/karolbystrek/kairos/issues/<parent>/sub_issues -X POST
-F sub_issue_id=<child-database-id>` for native parent/child links. Obtain the
child database ID with `gh api repos/karolbystrek/kairos/issues/<child> --jq .id`.

## Issue audits

During issue audits, compare scope with current source and history. Leave
unfinished issues open. Close previously implemented issues with a comment
explaining the evidence and linking the implementing PR or commit; mention
later accepted replacements rather than promising superseded behavior.
