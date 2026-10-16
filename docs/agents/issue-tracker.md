# GitHub issues

## Contents

- [GitHub issues](#github-issues)
- [Contents](#contents)
- [Commands and sub-issues](#commands-and-sub-issues)
- [Issue audits](#issue-audits)

The end-to-end workflow lives in [AGENTS.md](../../AGENTS.md#workflow). This
file covers the GitHub mechanics. Use descriptive issue titles without an agent
prefix, and pass multi-line bodies with `--body-file`.

## Commands and sub-issues

Use `gh issue create/view/list/edit/comment/close` and `gh pr create`. Link a
native sub-issue with `gh api repos/karolbystrek/kairos/issues/<parent>/sub_issues
-X POST -F sub_issue_id=<child-database-id>`, getting the child database ID from
`gh api repos/karolbystrek/kairos/issues/<child> --jq .id`. Close a parent issue
only when all of its scope is complete.

## Issue audits

Compare each issue's scope with current source and history. Leave unfinished
issues open. Close implemented issues with a comment giving the evidence and
linking the implementing PR or commit; mention later accepted replacements
rather than promising superseded behavior.
