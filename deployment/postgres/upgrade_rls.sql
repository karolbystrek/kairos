-- Operator-only. Stop API/worker writes first; run with ON_ERROR_STOP.
-- prepare_upgrade.py supplies exact rls_objects.sql and Flyway checksums.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('upgrade.owner', :'ownerUser',true),set_config('upgrade.runtime', :'runtimeUser',true),
       set_config('upgrade.old_checksum', :'oldChecksum',true),set_config('upgrade.new_checksum', :'newChecksum',true);
CREATE TEMP TABLE upgrade_expected_schema(value text);
INSERT INTO upgrade_expected_schema VALUES('426650806ac73ae5792cc0af351c6cd5');
DO $$
DECLARE actual text; old_owner oid;
BEGIN
    IF current_setting('upgrade.owner') !~ '^[a-z][a-z0-9_]{0,62}$'
        OR current_setting('upgrade.runtime') !~ '^[a-z][a-z0-9_]{0,62}$'
        OR current_setting('upgrade.owner')=current_setting('upgrade.runtime') THEN
        RAISE EXCEPTION 'Invalid owner/runtime roles';
    END IF;
    IF to_regnamespace('kairos_security') IS NOT NULL THEN RAISE EXCEPTION 'Upgrade already applied'; END IF;
    IF current_setting('upgrade.old_checksum')::integer<>-1882119370
        OR (SELECT count(*) FROM public.flyway_schema_history)<>1 OR NOT EXISTS(
        SELECT 1 FROM public.flyway_schema_history WHERE version='1' AND success
            AND checksum=current_setting('upgrade.old_checksum')::integer) THEN
        RAISE EXCEPTION 'Unknown Flyway schema/checksum';
    END IF;
    SELECT md5(string_agg(definition,E'\n' ORDER BY definition)) INTO actual FROM (
        SELECT 'C:' || c.relname || ':' || a.attnum || ':' || a.attname || ':' || format_type(a.atttypid,a.atttypmod)
            || ':' || a.attnotnull::text || ':' || a.attidentity::text || ':' || COALESCE(pg_get_expr(d.adbin,d.adrelid),'') AS definition
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid
        LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
        WHERE n.nspname='public' AND c.relkind='r' AND c.relname<>'flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped
        UNION ALL SELECT 'K:' || c.relname || ':' || k.conname || ':' || pg_get_constraintdef(k.oid)
        FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
        UNION ALL SELECT 'I:' || pg_get_indexdef(i.indexrelid) FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
        UNION ALL SELECT 'T:' || pg_get_triggerdef(t.oid) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal
        UNION ALL SELECT 'F:' || p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
    ) definitions;
    IF actual<>(SELECT value FROM upgrade_expected_schema) THEN RAISE EXCEPTION 'Unexpected old schema: %',actual; END IF;
    IF (SELECT count(DISTINCT c.relowner) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','S'))<>1 THEN RAISE EXCEPTION 'Unexpected mixed owners'; END IF;
    IF EXISTS(SELECT 1 FROM pg_roles r WHERE r.rolname IN (current_setting('upgrade.owner'),current_setting('upgrade.runtime'))
        AND (r.rolsuper OR r.rolbypassrls OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication))
        OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member
            WHERE r.rolname IN (current_setting('upgrade.owner'),current_setting('upgrade.runtime'))) THEN
        RAISE EXCEPTION 'Unexpected privileged owner/runtime roles';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_setting('upgrade.owner'))
        OR NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_setting('upgrade.runtime')) THEN
        RAISE EXCEPTION 'Provision approved restricted roles before upgrade';
    END IF;
    IF EXISTS(SELECT 1 FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE r.rolname=current_setting('upgrade.runtime')) THEN
        RAISE EXCEPTION 'Runtime owns objects';
    END IF;
END $$;
DROP TABLE upgrade_expected_schema;
-- Triggers protect future writes; validate their invariants for retained rows too.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM public.orders o JOIN public.locations l ON l.id=o.location_id
        JOIN public.external_integrations i ON i.id=o.external_integration_id WHERE i.tenant_id<>l.tenant_id) THEN
        RAISE EXCEPTION 'Order integration tenant mismatch' USING ERRCODE='23503';
    END IF;
    IF EXISTS(SELECT 1 FROM public.webhook_deliveries d JOIN public.order_outbox_events e ON e.id=d.outbox_event_id
        JOIN public.webhook_subscriptions s ON s.id=d.subscription_id WHERE s.tenant_id<>e.tenant_id) THEN
        RAISE EXCEPTION 'Webhook recipient tenant mismatch' USING ERRCODE='23503';
    END IF;
    IF EXISTS(SELECT 1 FROM public.webhook_delivery_signing_versions v JOIN public.webhook_deliveries d ON d.id=v.delivery_id
        JOIN public.webhook_signing_secret_versions s ON s.id=v.signing_secret_version_id WHERE s.subscription_id<>d.subscription_id) THEN
        RAISE EXCEPTION 'Captured signing subscription mismatch' USING ERRCODE='23503';
    END IF;
END $$;
-- New constraints validate the remaining retained relationships.
ALTER TABLE public.locations DROP CONSTRAINT locations_live_normalized_name_check;
ALTER TABLE public.locations DROP CONSTRAINT locations_tenant_live_name_key;
ALTER TABLE public.locations DROP COLUMN live_normalized_name;
ALTER TABLE public.locations ALTER COLUMN normalized_name TYPE varchar(240);
ALTER TABLE public.locations DROP CONSTRAINT locations_normalized_name_check;
ALTER TABLE public.locations ADD CONSTRAINT locations_normalized_name_check
    CHECK (TRIM(normalized_name)<>'' AND normalized_name=TRIM(normalized_name));
CREATE UNIQUE INDEX locations_tenant_live_name_key ON public.locations(tenant_id,normalized_name) WHERE status<>'ARCHIVED';
ALTER TABLE public.customer_push_deliveries DROP COLUMN endpoint_fingerprint;
ALTER TABLE public.customer_push_deliveries DROP COLUMN push_service_origin;
ALTER TABLE public.order_history DROP CONSTRAINT order_history_initiator_check;
ALTER TABLE public.order_history ADD CONSTRAINT order_history_initiator_check CHECK (
    (initiator_type IS NULL AND initiator_id IS NULL AND initiator_api_key_id IS NULL AND initiator_api_key_version_id IS NULL)
    OR (initiator_type IS NOT NULL AND initiator_type='SYSTEM' AND initiator_id IS NULL AND initiator_api_key_id IS NULL AND initiator_api_key_version_id IS NULL)
    OR (initiator_type IS NOT NULL AND initiator_type='USER' AND initiator_id IS NOT NULL AND initiator_api_key_id IS NULL AND initiator_api_key_version_id IS NULL)
    OR (initiator_type IS NOT NULL AND initiator_type='INTEGRATION' AND initiator_id IS NOT NULL AND initiator_api_key_id IS NOT NULL AND initiator_api_key_version_id IS NOT NULL));
DO $$
DECLARE object record;
BEGIN
    FOR object IN SELECT c.relname,c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO %I',CASE WHEN object.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END,
            object.relname,current_setting('upgrade.owner'));
    END LOOP;
    EXECUTE format('ALTER SCHEMA public OWNER TO %I',current_setting('upgrade.owner'));
    EXECUTE format('GRANT CONNECT,CREATE ON DATABASE %I TO %I',current_database(),current_setting('upgrade.owner'));
    EXECUTE format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC',current_database());
END $$;
SET LOCAL ROLE :"ownerUser";
\ir rls_objects.sql
RESET ROLE;
-- Check restricted-role denial before explicitly aligning the single Flyway entry.
SET LOCAL ROLE :"runtimeUser";
DO $$
BEGIN
    IF (SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user)
        OR EXISTS(SELECT 1 FROM public.tenants) OR EXISTS(SELECT 1 FROM public.orders) THEN
        RAISE EXCEPTION 'Runtime isolation verification failed';
    END IF;
END $$;
RESET ROLE;
UPDATE public.flyway_schema_history SET checksum=current_setting('upgrade.new_checksum')::integer
    WHERE version='1' AND checksum=current_setting('upgrade.old_checksum')::integer AND success;
COMMIT;
