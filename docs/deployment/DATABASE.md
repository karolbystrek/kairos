# PostgreSQL roles and reviewed RLS upgrade

## Contents

- [PostgreSQL roles and reviewed RLS upgrade](#postgresql-roles-and-reviewed-rls-upgrade)
- [Contents](#contents)
- [Credentials and fresh startup](#credentials-and-fresh-startup)
- [Existing database maintenance](#existing-database-maintenance)
- [Prepare the exact upgrade](#prepare-the-exact-upgrade)
- [Execute and verify](#execute-and-verify)
- [Failure and rollback boundary](#failure-and-rollback-boundary)

## Credentials and fresh startup

`POSTGRES_USER`/`POSTGRES_PASSWORD` remain the PostgreSQL initialization and
provider-administration credentials. The API receives two separate credential
pairs: `KAIROS_DB_OWNER_USER`/`KAIROS_DB_OWNER_PASSWORD` for Flyway, and
`KAIROS_DB_RUNTIME_USER`/`KAIROS_DB_RUNTIME_PASSWORD` for Spring's datasource.
Use different role names and strong passwords. Role names are lowercase SQL
identifiers starting with a letter, at most 63 characters.

Both application roles have `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
NOBYPASSRLS`. The owner owns the application schema and performs migrations;
the runtime has no ownership, role membership, schema creation or table truncate
rights. Only explicit application table/helper grants are supplied. Spring
Session tables have infrastructure DML grants without fabricated tenant context.
The API startup process still holds migration credentials: this separation
protects runtime queries, not a fully compromised API process.

For a fresh database, Compose's `postgres-bootstrap` one-shot service creates
and checks the restricted roles before API startup. Hosted `deploy.sh` explicitly
runs and waits for it before its `--no-deps` API start. Bootstrap rejects an
unexpected privileged existing role and an old applied schema; it never repairs
Flyway history or performs the operator upgrade. Environment password changes
are not a general rotation procedure; coordinate role and configuration updates
in an approved maintenance window.

API startup checks the actual runtime role, schema revision, ownership and
required grants and fails closed when isolation prerequisites are missing.
Normal tenant operations establish transaction-local persisted staff or exact
API-key authority. Anonymous tracking and verified push capabilities use narrow
reference scopes. Workers select and lock one eligible identity, then bind its
exact processing or completion scope. No ordinary query receives owner access.

## Existing database maintenance

Upgrading an already applied V1 is an operator-only, explicitly approved action.
The supported old schema is the V1 at Git revision `66d35d7`, with Flyway checksum
`-1882119370`. The upgrade also verifies its exact schema fingerprint and single
successful V1 history entry; an unknown revision, modified schema, mixed object
owners or repeated upgrade aborts. Do not bypass these checks.

Agree a maintenance window and stop application/worker writes using the
operator's approved procedure. Preserve PostgreSQL and provider data, volumes,
configuration and matching encryption/master/signing keys. The current disposable
VPS [backup policy](../requirements/routing-releases.md#development-operations-policy)
remains unchanged; a data-preserving upgrade is not a backup or recovery plan.

Provision the new owner and runtime roles through trusted database administration
before upgrading, with the flags above and no inherited memberships. Set their
passwords through a protected credential mechanism such as interactive `\password`,
and reconcile `production.env` without exposing credentials in command arguments
or logs. Automatic bootstrap deliberately refuses the old applied schema.

## Prepare the exact upgrade

Use the reviewed release bundle and a trusted checkout containing `66d35d7`.
The bundle includes V1, `bootstrap.sh`, `prepare_upgrade.py` and `upgrade_rls.sql`.
In a private operator workspace, prepare the old fixture and exact new objects:

```sh
umask 077
mkdir /private/path/kairos-upgrade
git show 66d35d7:apps/api/src/main/resources/db/migration/V1__create_initial_schema.sql \
  > /private/path/kairos-upgrade/old-v1.sql
python3 /srv/kairos/releases/REVIEWED_REVISION/deployment/postgres/prepare_upgrade.py \
  /srv/kairos/releases/REVIEWED_REVISION/apps/api/src/main/resources/db/migration/V1__create_initial_schema.sql \
  /private/path/kairos-upgrade/old-v1.sql /private/path/kairos-upgrade/prepared \
  --runtime kairos_runtime
cp /srv/kairos/releases/REVIEWED_REVISION/deployment/postgres/upgrade_rls.sql \
  /private/path/kairos-upgrade/prepared/upgrade_rls.sql
```

Replace example paths, revision and role with reviewed values. Preparation creates
`rls_objects.sql` and `checksums.txt`; verify the old checksum above and review the
computed new checksum against the exact V1 shipped in the selected release.
Retain that revision, checksum pair and reviewed artifacts as operator evidence.
Do not run a generated upgrade from a moving checkout or edit its checksum to
silence Flyway. The include is relative to the copied upgrade script, so keep the
two SQL files together.

## Execute and verify

Use a trusted PostgreSQL administrative connection configured outside the release,
for example a protected libpq service/password file. The following is an operator
command template; `NEW_CHECKSUM` is the reviewed value in `checksums.txt`:

```sh
psql -X --dbname=service=kairos-maintenance -v ON_ERROR_STOP=1 \
  -v ownerUser=kairos_owner -v runtimeUser=kairos_runtime \
  -v oldChecksum=-1882119370 -v newChecksum=NEW_CHECKSUM \
  -f /private/path/kairos-upgrade/prepared/upgrade_rls.sql
```

The script validates old schema/data/roles, transforms the accepted constraints
and derived columns, installs ownership/helpers/policies and checks missing-context
denial as the restricted runtime. It then explicitly aligns the one V1 history
entry to the new checksum in the same transaction. It never uses startup Flyway
repair, data reset or migration-history deletion.

After success, use the reviewed normal deployment path and verify startup under
the runtime role, retained representative accounts/orders/deliveries, scoped
staff/integration/customer operations and workers. Record live acceptance
separately. Repository tests use disposable old-V1 fixtures to verify preserved
rows, schema equivalence and rollback; they do not establish live acceptance.
The existing production Environment reviewer gate still applies.

## Failure and rollback boundary

Any upgrade SQL error aborts its transaction before checksum alignment commits;
keep the previous application stopped while diagnosing the rejected precondition
or data constraint. Do not reset volumes or run automatic repair. Provisioned
roles and environment edits occur separately and must be assessed independently.

After a committed upgrade, reverting only an image cannot restore the old schema
or checksum. Use a separately reviewed forward correction, or an explicitly
approved complete database restoration with its matching configuration and keys
when such a recovery source exists. Deployment never executes this upgrade or
attempts a schema rollback automatically.
