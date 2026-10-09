# GitHub release protection

## Contents

- [GitHub release protection](#github-release-protection)
- [Contents](#contents)
- [4. Protect GitHub releases before enabling deployment](#4-protect-github-releases-before-enabling-deployment)
- [Temporary deployment SSH access](#temporary-deployment-ssh-access)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 4. Protect GitHub releases before enabling deployment

An authorized repository owner configures the `production` Environment before
adding deployment secrets or approving a run. A workflow reference alone may
create an unprotected Environment. Use `gh` for all GitHub operations.

Configure required reviewer `karolbystrek`, allow that owner to approve their own
initiated runs (`prevent_self_review=false`), and allow administrator bypass
(`can_admins_bypass=true`) as selected by the owner. Select custom deployment branch policies with **only branch
`main`**, no tag policy; do not use “all protected branches” as a substitute.
The owner can apply these through `gh api` using the Environment and deployment
branch-policy endpoints and verify the saved result with:

```bash
gh api repos/karolbystrek/kairos/environments/production
gh api repos/karolbystrek/kairos/environments/production/deployment-branch-policies
```

Check plan/repository visibility supports required reviewers; if unavailable,
record it as a release blocker. Administrator bypass remains allowed under the
owner-approved policy. See [GitHub Environment protection](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

Set non-secret **repository** variables `NEXT_PUBLIC_API_BASE_URL` and
`NEXT_PUBLIC_CUSTOMER_APP_URL` to the production values using `gh variable set`.
Validation/publication jobs run before the Environment gate and cannot read
Environment variables. Frontend URLs are compiled into images; changing these
variables requires a new source revision/build, because existing revision tags
are immutable. Keep runtime origins consistent with those build inputs.

Install these four secrets with `gh secret set --env production` from protected
files or secure prompts, never literal secret arguments or repository-level copies:

| Environment secret | Value |
| --- | --- |
| `DEPLOY_HOST` | Verified reachable deployment host |
| `DEPLOY_USER` | Dedicated restricted Linux deployment username |
| `DEPLOY_SSH_KEY` | Matching private SSH key authorized for that account |
| `DEPLOY_KNOWN_HOSTS` | Independently verified OpenSSH host-key entries |

Do not reuse the GitHub publication token as host GHCR credentials. Before the
first authorized release, inspect the successful main CI run and manually start
[the deployment workflow](RELEASES.md). Its release-selection job resolves an
eligible published artifact before `Deploy production` waits for approval.
CI publication never requests production approval.
While approval is withheld, no deployment steps/SSH connection may run and
Environment secrets must remain gated. Verify owner self-approval, main-only
policy, and rejected/withheld approval behavior; record results without secrets.
Merging a PR or publishing images does not authorize approving deployment.

### Temporary deployment SSH access

Before approving a release, complete [AWS OIDC setup](AWS-SSH.md). Keep the
administrator and Lightsail browser SSH rules; do not allow GitHub's full runner
range list or public TCP22. The protected job obtains temporary AWS credentials,
recovers a tagged stale /32, opens its own /32 and verifies TCP access. Existing
strict SSH host-key authentication remains mandatory. An `always()` cleanup
closes and verifies the temporary rule after success or failure. Its outcome is
included in the job summary and cleanup failure fails the job.

Forced termination and AWS failures can leave a temporary rule. Recover using
the documented command before retrying; never delete an arbitrary /32 or clear
the marker without closing its rule. AWS session expiry does not expire firewall
rules. Do not run manual recovery during an active deployment. Live OIDC, cleanup
on failure and interrupted-run recovery acceptance remain operator checks.
