# GitHub release protection

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## Protect GitHub releases before enabling deployment

An authorized repository owner configures the `production` Environment before
adding deployment secrets or dispatching a deployment. A workflow reference alone may
create an unprotected Environment. Use `gh` for all GitHub operations.

Select custom deployment branch policies with **only branch `main`**, no tag
policy; do not use “all protected branches” as a substitute. No required
reviewer is configured: the owner's manual dispatch is the production approval.
The owner can apply these through `gh api` using the Environment and deployment
branch-policy endpoints and verify the saved result with:

```bash
gh api repos/karolbystrek/kairos/environments/production
gh api repos/karolbystrek/kairos/environments/production/deployment-branch-policies
```

See [GitHub Environment protection](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

Set non-secret **repository** variables `NEXT_PUBLIC_API_BASE_URL`,
`NEXT_PUBLIC_CUSTOMER_APP_URL` and `NEXT_PUBLIC_PANEL_APP_URL` to the production
values using `gh variable set`. `NEXT_PUBLIC_PANEL_APP_URL` must equal the
runtime `PANEL_APP_URL`; the panel uses it for canonical and link-preview URLs.
Validation and image build jobs run outside the Environment and cannot read
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

The same Environment also holds runtime configuration that deployment writes to
the host; see [configuration](CONFIGURATION.md) for required keys.

Do not reuse the GitHub publication token as host GHCR credentials. For a
release, manually start [the deployment workflow](RELEASES.md). It validates the
dispatch revision with CI, builds and publishes its images and creates the
release bundle before `Deploy production` runs. CI never builds or publishes
images. Only the `Deploy production` job reads Environment secrets; verify the
main-only policy and record results without secrets. Merging a PR does not
authorize dispatching a deployment.

### Temporary deployment SSH access

Before dispatching a release, complete [AWS OIDC setup](AWS-SSH.md). Keep the
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
