# PostgreSQL roles and initialization

## Contents

- [PostgreSQL roles and initialization](#postgresql-roles-and-initialization)
- [Contents](#contents)
- [Credentials and fresh startup](#credentials-and-fresh-startup)
- [Spring persistence and RLS](#spring-persistence-and-rls)
- [Development schema changes](#development-schema-changes)

## Credentials and fresh startup

`POSTGRES_USER`/`POSTGRES_PASSWORD` are PostgreSQL initialization and provider
administration credentials. Configure two additional credential pairs in the
normal VPS `production.env`: `KAIROS_DB_OWNER_USER`/`KAIROS_DB_OWNER_PASSWORD`
for Flyway, and `KAIROS_DB_RUNTIME_USER`/`KAIROS_DB_RUNTIME_PASSWORD` for Spring's
datasource. The VPS bootstrap publishes their non-secret defaults in
`production.env.example`; the operator supplies passwords before deployment.
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
ready. Spring then connects as the owner to apply V1 through Flyway automatically.
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

## Development schema changes

The current development policy edits V1 in place. The VPS data is disposable and
the owner will recreate the Compose stack and database volume for this revision;
no data-preserving upgrade or checksum repair is supplied. Recreating containers
while retaining the PostgreSQL volume does not re-run role initialization or V1.
Any volume reset is an operator action requiring explicit authorization.

After initialization, deploy the reviewed revision through the existing production
Environment approval gate and verify application health and representative scoped
operations. No repository script resets volumes or repairs Flyway history.
