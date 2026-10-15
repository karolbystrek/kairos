# PostgreSQL roles and initialization

## Contents

- [PostgreSQL roles and initialization](#postgresql-roles-and-initialization)
- [Contents](#contents)
- [Credentials and fresh startup](#credentials-and-fresh-startup)
- [Spring persistence and RLS](#spring-persistence-and-rls)
- [Platform administrator grants](#platform-administrator-grants)
  - [Grant platform access](#grant-platform-access)
  - [Verify and revoke](#verify-and-revoke)

## Credentials and fresh startup

`POSTGRES_USER`/`POSTGRES_PASSWORD` are PostgreSQL initialization and provider
administration credentials. Configure two additional credential pairs in the
normal VPS `production.env`: `KAIROS_DB_OWNER_USER`/`KAIROS_DB_OWNER_PASSWORD`
for Flyway, and `KAIROS_DB_RUNTIME_USER`/`KAIROS_DB_RUNTIME_PASSWORD` for Spring's
datasource. `deployment/production.env.example` holds their non-secret defaults;
the operator supplies passwords as GitHub `production` Environment secrets.
Role names must differ from each other and from the initial administrator.
They are lowercase SQL identifiers starting with a letter, at most 63 characters.

Both application roles have `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
NOBYPASSRLS` and no inherited memberships. The owner owns the application schema
and applies migrations; runtime has no ownership, role membership, schema creation
or table truncate rights. The API does not receive initialization administrator
credentials. Its startup process holds the separate Flyway credentials, so this
protects ordinary runtime queries rather than a fully compromised API process.

Compose mounts `deployment/postgres/bootstrap.sh` into PostgreSQL's standard
`docker-entrypoint-initdb.d` directory. On an empty database volume, PostgreSQL
creates its initial database, runs this role initialization script, and becomes
ready. Spring then connects as the owner to apply migrations through Flyway automatically.
V1 is frozen: every schema change goes into a new `V<n>__*.sql` migration.
There is no separate database-bootstrap service or deployment stage.

Initialization scripts run only for an empty database volume. Editing environment
passwords does not rotate existing roles. Coordinate credential changes through
an approved maintenance procedure; preserve configuration and application keys.

## Spring persistence and RLS

Business workflows and normal persistence use Java domain methods and Hibernate
repositories. Each protected service transaction binds verified transaction-local
identity or exact customer capability context on its own connection; PostgreSQL
applies RLS to Hibernate queries automatically. Joined transactions cannot change
authority, and context disappears after commit or rollback.

Registration and invitation redemption use exact new-account/bearer scopes and
ordinary entity persistence. Minimal credential-bootstrap projections, worker
discovery, and capability-specific aggregates/cancellation use native queries in
persistence repositories. Workers lock one eligible identity and complete only
with its current claim token. No ordinary query receives owner access.

API startup verifies the runtime role, ownership, policy revision and required
grants. Repository tests exercise actual fresh PostgreSQL initialization, Flyway,
runtime denial and scoped workflows; they do not establish hosted acceptance.

## Platform administrator grants

[Platform authority](../requirements/analytics-reporting.md#platform-authority)
is added to an existing enabled staff account; it is never self-service. Only
the owner role can write `platform_administrators`. Granting or revoking it
changes live production data, so it needs the owner's explicit approval.

The commands run on the production host as the deployment account. They find
the `kairos` project's PostgreSQL container through Compose labels, and the
container's own environment supplies the owner role and database name. The
image trusts local socket connections, so no password is entered and nothing
secret appears in the command line.

### Grant platform access

1. Create the account through normal registration or invitation, sign in once
   and finish onboarding. Note its email address.
2. SSH to the production host. Replace the email on the `\set` line, then run:

   ```sh
   postgres=$(docker ps -q --filter label=com.docker.compose.project=kairos \
     --filter label=com.docker.compose.service=postgres)
   docker exec -i "$postgres" sh -c \
     'psql -X -v ON_ERROR_STOP=1 -U "$KAIROS_DB_OWNER_USER" -d "$POSTGRES_DB"' <<'SQL'
   \set email 'admin@example.com'
   INSERT INTO platform_administrators (account_id)
   SELECT id FROM accounts WHERE email = lower(:'email') AND status = 'ENABLED'
   ON CONFLICT DO NOTHING
   RETURNING account_id;
   SQL
   ```

3. Expect one returned `account_id` and `INSERT 0 1`. `INSERT 0 0` with no row
   means the account is unknown, not enabled, or already granted; check it
   before retrying.
4. Reload the panel as that account. The Platform tab appears without signing
   in again because every request rechecks the grant.

### Verify and revoke

Run the same command with a different SQL body. To list current grants:

```sql
SELECT a.email, a.status, p.granted_at
FROM platform_administrators p JOIN accounts a ON a.id = p.account_id
ORDER BY p.granted_at;
```

To revoke one, keeping the `\set email` line:

```sql
DELETE FROM platform_administrators
WHERE account_id = (SELECT id FROM accounts WHERE email = lower(:'email'))
RETURNING account_id;
```

Revocation takes effect on the account's next request. Disabling or deleting
the account also ends platform access, and account purge removes the grant.
