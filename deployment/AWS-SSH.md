# Temporary SSH access through AWS OIDC

## Contents

- [Temporary SSH access through AWS OIDC](#temporary-ssh-access-through-aws-oidc)
- [Contents](#contents)
- [Operator setup](#operator-setup)
- [Read inputs and render policy](#read-inputs-and-render-policy)
- [Provision IAM with explicit approval](#provision-iam-with-explicit-approval)
- [Configure deployment variables](#configure-deployment-variables)
- [Failure and interrupted-run recovery](#failure-and-interrupted-run-recovery)

Agent execution of AWS/Cloudflare resource changes requires explicit user
approval; read-only inspection is allowed. See [cloud operations](../docs/agents/cloud-operations.md).

The production job obtains AWS credentials only after the protected `production`
Environment approves it. No long-lived AWS access keys are stored in GitHub.
The current deployment account/SSH key remains unchanged. This does not deploy
applications or relax the permanent SSH/HTTPS firewall rules.

## Operator setup

### Read inputs and render policy

Use a trusted checkout and AWS CLI credentials authorized to create the IAM
provider/role. The commands below are for this instance; choose the correct
region/name if provisioning a replacement.

```sh
export AWS_REGION=eu-central-1
export DEPLOY_INSTANCE=kairos-production
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export INSTANCE_ARN=$(aws lightsail get-instance --instance-name "$DEPLOY_INSTANCE" \
  --region "$AWS_REGION" --query instance.arn --output text)
export OIDC_SUBJECT="$(gh api repos/karolbystrek/kairos/actions/oidc/customization/sub \
  --jq .sub_claim_prefix):environment:production"
mkdir -p "$HOME/.config/kairos/aws"
python3 - <<'PY'
import json, os
from pathlib import Path
from string import Template
root = Path.home() / '.config/kairos/aws'
for name in ['ssh-role-trust.json', 'ssh-role-policy.json']:
    document = Template((Path('deployment/aws') / name).read_text()).substitute(os.environ)
    json.loads(document)
    (root / name).write_text(document)
PY
```

Use the exact repository OIDC prefix: newly created repositories can include
immutable owner/repository IDs. Keep the `production` Environment reviewer and
main-only branch restriction in place; the Environment subject itself does not
include a branch. See [GitHub OIDC in AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).

### Provision IAM with explicit approval

Check whether the provider already exists with
`aws iam list-open-id-connect-providers`. If absent, create it:

```sh
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

If present, reuse it and ensure its client ID list includes `sts.amazonaws.com`;
do not replace a shared provider. AWS retrieves the thumbprint automatically.
Create the dedicated role if absent; if already provisioned, inspect its trust
and inline policies before applying reviewed updates:

```sh
aws iam create-role --role-name kairos-production-deploy-ssh \
  --max-session-duration 7200 \
  --assume-role-policy-document "file://$HOME/.config/kairos/aws/ssh-role-trust.json"
aws iam put-role-policy --role-name kairos-production-deploy-ssh \
  --policy-name KairosDeploymentSsh \
  --policy-document "file://$HOME/.config/kairos/aws/ssh-role-policy.json"
```

The role can open/close ports only on this instance, and tag/untag only
`kairos-deploy-ssh`. Lightsail does not support IAM conditions that restrict those
port operations to TCP22. The instance/firewall/operation read APIs require
`Resource: *`, limited to the selected region. It cannot access SSH private keys,
manage IAM, replace all firewall rules, reboot/delete instances or change other
instance tags. See [Lightsail IAM scope](https://docs.aws.amazon.com/service-authorization/latest/reference/list_lightsail.html).

### Configure deployment variables

Set these **production Environment variables**, not secrets. For administrator
CIDRs, list every permanent administrator IPv4 range, separated by commas. Update
that variable when changing your permanent SSH sources; never include runner IPs.

```sh
gh variable set DEPLOY_AWS_REGION --env production --repo karolbystrek/kairos --body "$AWS_REGION"
gh variable set DEPLOY_INSTANCE --env production --repo karolbystrek/kairos --body "$DEPLOY_INSTANCE"
gh variable set DEPLOY_AWS_ACCOUNT_ID --env production --repo karolbystrek/kairos --body "$AWS_ACCOUNT_ID"
gh variable set DEPLOY_AWS_ROLE_ARN --env production --repo karolbystrek/kairos \
  --body "arn:aws:iam::$AWS_ACCOUNT_ID:role/kairos-production-deploy-ssh"
gh variable set DEPLOY_ADMIN_CIDRS --env production --repo karolbystrek/kairos --body '89.67.6.243/32'
```

The 7200-second session covers the job's 70-minute timeout; do not shorten it below
the job duration. Workflow auth/open/cleanup all use the same runner. No temporary
SSH access is created during this setup. Keep the four SSH deployment secrets in
`production`. The runner checks the instance public IPv4 against `DEPLOY_HOST`.

## Failure and interrupted-run recovery

The `kairos-deploy-ssh` tag contains `<run-id>-<attempt>:<IPv4>/32`. It is written
before opening access, and removed only after closure is verified. Ordinary
failed deployment steps still execute cleanup. Forced runner termination, AWS
outages or credential expiry can leave the rule; expiry is not a firewall TTL.
The next serialized deployment recovers the tagged rule before opening its own.
Cleanup waits for accepted open operations to finish. If opening fails with an
uncertain outcome, it retains the marker even after checking closure, so a later
run can recover a delayed open. Other unmarked rules stay operator-owned and are never adopted or deleted.

For immediate manual recovery, ensure no deployment is running, then from the
trusted checkout on your workstation (AWS CLI credentials required):

```sh
export AWS_REGION=eu-central-1
export DEPLOY_INSTANCE=kairos-production
export DEPLOY_HOST=52.58.250.75
export DEPLOY_ADMIN_CIDRS=89.67.6.243/32
export GITHUB_RUN_ID=0 GITHUB_RUN_ATTEMPT=0
python3 deployment/ssh_firewall.py recover
```

Never clear the tag by itself or delete arbitrary SSH rules. If the marker is
invalid or overlaps administrator access, investigate manually rather than
forcing removal. Review firewall state with `aws lightsail get-instance-port-states
--instance-name kairos-production --region eu-central-1`. Marker state is metadata,
not a credential. No scheduled cleanup is provided.

Repository checks use a fake AWS boundary to exercise lifecycle/preservation,
invalid input, interruption recovery and cleanup failure. They do not prove a
live OIDC exchange or runner connection. An approved first-deployment acceptance
must verify OIDC, success/failure cleanup and interrupted-run recovery separately.
