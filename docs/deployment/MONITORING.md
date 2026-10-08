# Native Lightsail email alarms

## Contents

- [Native Lightsail email alarms](#native-lightsail-email-alarms)
- [Contents](#contents)
- [Coverage and thresholds](#coverage-and-thresholds)
- [Verify the regional email contact](#verify-the-regional-email-contact)
- [Create the alarms](#create-the-alarms)
- [Verify notification delivery](#verify-notification-delivery)
- [Response and maintenance](#response-and-maintenance)
- [Activation status](#activation-status)

Use AWS-provided instance metrics for the disposable development VPS, following
the [operations policy](../requirements/routing-releases.md#development-operations-policy).
No host installation, monitoring agent, custom script or additional service is
needed. Operator/responder: Karol Bystrek. Email destination stays private.

## Coverage and thresholds

Target: Lightsail instance `kairos-production`, region `eu-central-1`.
These are initial development thresholds; tune deliberately after observing load.

| Alarm name | Metric and threshold | Breaching periods | Missing data |
| --- | --- | --- | --- |
| `kairos-instance-status` | `StatusCheckFailed >= 1` | 1 of 1 | Breaching |
| `kairos-high-cpu` | `CPUUtilization >= 80%` | 3 of 3 | Missing |
| `kairos-low-burst` | `BurstCapacityPercentage <= 20%` | 3 of 3 | Missing |

Each Lightsail alarm period is five minutes. CPU and burst alarms therefore
evaluate three five-minute samples; missing-data handling can affect detection
time. Notify on `ALARM`, `OK` and `INSUFFICIENT_DATA` so recovery and lost metric
coverage are visible. These are state-change notifications, not repeated pages.
See [alarm behavior](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-alarms.html).

The combined status metric covers instance and AWS system checks. CPU and burst
capacity indicate compute pressure, not available RAM or disk space. Native
[instance metrics](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-viewing-instance-health-metrics.html)
do not provide disk utilization, memory utilization or public HTTPS probes;
those alerts are deferred. A healthy instance does not prove the applications,
Cloudflare path, authentication, SSE or push work. Lightsail monitoring/alerting
has [no additional charge](https://aws.amazon.com/lightsail/faq/); this setup uses
email only, with no SMS, CloudWatch agent or paid synthetic checks.

## Verify the regional email contact

Agents need explicit approval before creating/updating contacts, alarms or
triggering notification tests; see [cloud operations](../agents/cloud-operations.md).
The commands below describe the exact scope and do not authorize execution.
Read existing state first to avoid replacing unrelated configuration:

```sh
aws lightsail get-contact-methods --region eu-central-1
aws lightsail get-alarms --region eu-central-1 --monitored-resource-name kairos-production
```

Inspect contact output privately; it contains email addresses. Reuse the owner's
existing verified regional Email contact when present. Otherwise add the chosen
address through the Lightsail console's regional notification contacts, or run
`aws lightsail create-contact-method` with `--notification-protocol Email` and
the private `--contact-endpoint` supplied through protected CLI input. Never
record the real address in Git or issues. Open AWS's verification email and
confirm the contact is `Valid` in `eu-central-1` before enabling notifications.
See [contact verification](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-adding-editing-notification-contacts.html).

## Create the alarms

After approval, run from a trusted workstation with AWS credentials scoped to
this instance/region. These commands create or update only the named alarms;
inspect any same-name alarm before overwriting it. They do not change the VM,
firewall, IAM, application containers or deployment workflow.

```sh
aws lightsail put-alarm --region eu-central-1 \
  --alarm-name kairos-instance-status --monitored-resource-name kairos-production \
  --metric-name StatusCheckFailed --comparison-operator GreaterThanOrEqualToThreshold \
  --threshold 1 --evaluation-periods 1 --datapoints-to-alarm 1 \
  --treat-missing-data breaching --contact-protocols Email \
  --notification-triggers ALARM OK INSUFFICIENT_DATA --notification-enabled

aws lightsail put-alarm --region eu-central-1 \
  --alarm-name kairos-high-cpu --monitored-resource-name kairos-production \
  --metric-name CPUUtilization --comparison-operator GreaterThanOrEqualToThreshold \
  --threshold 80 --evaluation-periods 3 --datapoints-to-alarm 3 \
  --treat-missing-data missing --contact-protocols Email \
  --notification-triggers ALARM OK INSUFFICIENT_DATA --notification-enabled

aws lightsail put-alarm --region eu-central-1 \
  --alarm-name kairos-low-burst --monitored-resource-name kairos-production \
  --metric-name BurstCapacityPercentage --comparison-operator LessThanOrEqualToThreshold \
  --threshold 20 --evaluation-periods 3 --datapoints-to-alarm 3 \
  --treat-missing-data missing --contact-protocols Email \
  --notification-triggers ALARM OK INSUFFICIENT_DATA --notification-enabled
```

Review the returned operations and inspect `get-alarms` again. Confirm the
resource, thresholds, evaluation periods, missing-data policy, Email protocol,
notification triggers and enabled flag. An accepted API operation alone does not
prove the contact is verified or an email can reach the responder.

## Verify notification delivery

Use AWS's notification test for each alarm, without stopping instances, filling
disks or generating CPU load. In an approved test window, run:

```sh
aws lightsail test-alarm --region eu-central-1 --alarm-name kairos-instance-status --state ALARM
aws lightsail test-alarm --region eu-central-1 --alarm-name kairos-high-cpu --state ALARM
aws lightsail test-alarm --region eu-central-1 --alarm-name kairos-low-burst --state ALARM
aws lightsail test-alarm --region eu-central-1 --alarm-name kairos-instance-status --state OK
```

Confirm receipt of all three alarm emails and the recovery email. The
[test-alarm API](https://docs.aws.amazon.com/cli/latest/reference/lightsail/test-alarm.html)
tests notification delivery; it does not prove metric collection or simulate an
actual instance outage. Inspect current alarm state/metrics separately and record
passed/failed/blocked/not-run evidence in #18 without private email addresses.

For deployment failures, enable GitHub **Settings → Notifications → Actions**
email notifications for failed workflows, using the owner's verified address.
Confirm receipt on an actual failed workflow; do not break a production release
to create one. Account notification preferences cannot be assumed from a
successful Actions run. See [GitHub notification settings](https://docs.github.com/en/subscriptions-and-notifications/get-started/configuring-notifications).

## Response and maintenance

On a status email, inspect Lightsail status/metrics, then authorized bounded
container health/logs through trusted SSH. On CPU/burst emails, inspect sustained
load and workload behavior before proposing capacity changes. On insufficient
data, inspect instance state and AWS service health; do not treat it as healthy.
Fix applications forward through the approved release process. Never
automatically restart/delete instances, prune Docker or reset databases.

Review incidents/capacity weekly and OS/Docker/provider security updates monthly;
apply urgent security fixes promptly in an approved maintenance window. Track
Origin CA expiry through [maintenance](MAINTENANCE.md). Plan for alarm emails
during deliberate shutdowns; re-enable any deliberately paused notifications
afterward. Repeat delivery tests after changing contact/settings. Retire only
these named alarms with explicit approval; regional contacts can serve other
resources and must not be deleted as routine Kairos cleanup.

## Activation status

Repository preparation supplies this procedure. Regional contact verification,
three live alarms and email-delivery evidence are **pending operator activation**.
No active alert coverage or public-launch readiness is claimed.
