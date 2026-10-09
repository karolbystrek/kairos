#!/bin/sh
set -eu
for role in "$KAIROS_DB_OWNER_USER" "$KAIROS_DB_RUNTIME_USER"; do
    case "$role" in ''|*[!a-z0-9_]*|[0-9_]*) echo 'Database role names must be lowercase identifiers' >&2; exit 1;; esac
    [ "${#role}" -le 63 ] || exit 1
done
[ "$KAIROS_DB_OWNER_USER" != "$KAIROS_DB_RUNTIME_USER" ] || exit 1
[ "$POSTGRES_USER" != "$KAIROS_DB_OWNER_USER" ] || exit 1
[ "$POSTGRES_USER" != "$KAIROS_DB_RUNTIME_USER" ] || exit 1
: "${KAIROS_DB_OWNER_PASSWORD:?Set owner password}" "${KAIROS_DB_RUNTIME_PASSWORD:?Set runtime password}"
export PGPASSWORD="$POSTGRES_PASSWORD"
psql -X --no-psqlrc -v ON_ERROR_STOP=1 -h "${PGHOST:-/var/run/postgresql}" -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
\getenv owner_user KAIROS_DB_OWNER_USER
\getenv owner_password KAIROS_DB_OWNER_PASSWORD
\getenv runtime_user KAIROS_DB_RUNTIME_USER
\getenv runtime_password KAIROS_DB_RUNTIME_PASSWORD
BEGIN;
SELECT set_config('bootstrap.owner', :'owner_user', true),set_config('bootstrap.runtime', :'runtime_user',true);
DO $$
DECLARE role_name text;
BEGIN
    FOREACH role_name IN ARRAY ARRAY[current_setting('bootstrap.owner'),current_setting('bootstrap.runtime')] LOOP
        IF EXISTS(SELECT 1 FROM pg_roles r WHERE r.rolname=role_name AND
            (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls))
            OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname=role_name) THEN
            RAISE EXCEPTION 'Unexpected privileged database role';
        END IF;
        IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
            EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',role_name);
        END IF;
    END LOOP;
    IF EXISTS(SELECT 1 FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE r.rolname=current_setting('bootstrap.runtime')) THEN
        RAISE EXCEPTION 'Runtime must not own database objects';
    END IF;
END $$;
SELECT format('ALTER ROLE %I PASSWORD %L', :'owner_user', :'owner_password') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'runtime_user', :'runtime_password') \gexec
SELECT format('GRANT CONNECT,CREATE ON DATABASE %I TO %I', current_database(), :'owner_user') \gexec
SELECT format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC',current_database()) \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'owner_user') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
COMMIT;
SQL
