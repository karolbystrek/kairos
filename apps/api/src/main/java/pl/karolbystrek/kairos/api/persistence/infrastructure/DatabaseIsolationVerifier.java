package pl.karolbystrek.kairos.api.persistence.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseIsolationVerifier implements ApplicationRunner {
    private final JdbcTemplate database;

    @Override
    public void run(ApplicationArguments arguments) {
        var valid = database.queryForObject("""
            SELECT NOT (r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb OR r.rolreplication)
                AND NOT EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid)
                AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname IN ('public','kairos_security') AND c.relowner=r.oid)
                AND NOT has_schema_privilege(current_user,'public','CREATE')
                AND NOT has_schema_privilege(current_user,'kairos_security','CREATE')
                AND NOT has_database_privilege(current_user,current_database(),'CREATE')
                AND NOT has_database_privilege(current_user,current_database(),'TEMPORARY')
                AND (SELECT obj_description(oid,'pg_namespace') FROM pg_namespace WHERE nspname='kairos_security')='kairos-rls-v1'
                AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='public' AND c.relkind='r' AND c.relname NOT IN
                        ('spring_session','spring_session_attributes','flyway_schema_history')
                    AND (NOT c.relrowsecurity OR has_table_privilege(current_user,c.oid,'TRUNCATE')
                        OR EXISTS(SELECT 1 FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE']) privilege
                            WHERE NOT has_table_privilege(current_user,c.oid,privilege))
                        OR NOT EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid)))
                AND NOT EXISTS(SELECT 1 FROM (VALUES
                    ('tenants','rw'),('locations','arw'),('accounts','rw'),('location_assignments','rw'),
                    ('account_invitations','arw'),('external_integrations','arw'),('api_keys','arw'),
                    ('api_key_scopes','adrw'),('api_key_versions','adrw'),('api_key_location_access','adrw'),
                    ('webhook_subscriptions','arw'),('webhook_subscription_location_access','adrw'),
                    ('webhook_subscription_event_types','adrw'),('webhook_signing_secret_versions','adrw'),
                    ('orders','arw'),('order_history','ar'),('order_outbox_events','arw'),
                    ('webhook_deliveries','arw'),('webhook_delivery_signing_versions','ar'),
                    ('customer_push_subscriptions','drw'),('customer_push_enrollments','adr'),('customer_push_deliveries','adrw')
                ) expected(table_name,commands) WHERE expected.commands IS DISTINCT FROM (
                    SELECT string_agg(p.polcmd::text,'' ORDER BY p.polcmd::text) FROM pg_policy p
                        WHERE p.polrelid=to_regclass('public.' || expected.table_name)))
                AND (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                    AND p.proname IN ('valid_staff','lock_staff_location','lock_account_location','api_key_authentication','valid_integration','staff_authentication','identity_conflict',
                        'register_tenant','invitation_preview','redeem_invitation','verify_push_subscription','create_push_subscription',
                        'count_push_enrollments','cancel_push_deliveries','next_webhook_fanout','next_push_fanout','next_webhook_delivery','next_push_delivery',
                        'next_expired_push_subscription','next_dormant_push_subscription','next_terminal_push_delivery','valid_worker'))=22
                AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname IN ('kairos_security','public') AND p.prosecdef
                    AND (p.proowner=r.oid OR NOT has_function_privilege(current_user,p.oid,'EXECUTE')
                        OR NOT ('search_path=pg_catalog'=ANY(p.proconfig))
                        OR EXISTS(SELECT 1 FROM aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) acl WHERE acl.grantee=0 AND acl.privilege_type='EXECUTE')))
            FROM pg_roles r WHERE r.rolname=current_user
            """, Boolean.class);
        if (!Boolean.TRUE.equals(valid)) {
            throw new IllegalStateException("Restricted runtime database role and complete RLS configuration are required");
        }
    }
}
