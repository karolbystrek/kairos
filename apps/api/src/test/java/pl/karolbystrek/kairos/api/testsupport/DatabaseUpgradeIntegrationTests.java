package pl.karolbystrek.kairos.api.testsupport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DatabaseUpgradeIntegrationTests {
    @TempDir Path directory;
    JdbcTemplate owner;
    String[] checksums;

    @BeforeEach
    void oldSchema() throws Exception {
        var admin = PostgresTestDatabase.ownerDatabase();
        admin.execute("DROP DATABASE IF EXISTS upgrade_test WITH (FORCE)");
        admin.execute("CREATE DATABASE upgrade_test");
        var url = PostgresTestDatabase.adminUrl("upgrade_test");
        owner = new JdbcTemplate(new DriverManagerDataSource(url,"test","test"));
        var migrations = Files.createDirectory(directory.resolve("old"));
        var oldMigration = migrations.resolve("V1__create_initial_schema.sql");
        try (var source = getClass().getResourceAsStream("/pre-rls-v1.sql")) {
            Files.copy(source,oldMigration);
        }
        Flyway.configure().dataSource(url,"test","test").locations("filesystem:"+migrations).load().migrate();
        var root = Path.of("../..").toAbsolutePath().normalize();
        var prepared = directory.resolve("prepared");
        var prepare = new ProcessBuilder("python3",root.resolve("deployment/postgres/prepare_upgrade.py").toString(),
                root.resolve("apps/api/src/main/resources/db/migration/V1__create_initial_schema.sql").toString(),
                oldMigration.toString(),prepared.toString(),"--runtime","kairos_runtime").redirectErrorStream(true).start();
        var output = new String(prepare.getInputStream().readAllBytes());
        assertThat(prepare.waitFor()).as(output).isZero();
        checksums = Files.readString(prepared.resolve("checksums.txt")).lines().map(line -> line.split("=",2)[1]).toArray(String[]::new);
        Files.copy(root.resolve("deployment/postgres/upgrade_rls.sql"),prepared.resolve("upgrade_rls.sql"));
        directory = prepared;
    }

    @Test
    void upgradesRetainedDataAndMatchesFreshMigration() throws Exception {
        var tenant = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES(?)",tenant);
        var location=UUID.randomUUID(); var order=UUID.randomUUID(); var reference=UUID.randomUUID();
        owner.update("INSERT INTO locations(id,tenant_id,name,normalized_name,live_normalized_name) VALUES(?,?,'Retained','retained','retained')",location,tenant);
        owner.update("INSERT INTO orders(id,location_id,tracking_reference,label,status,created_at,updated_at) VALUES(?,?,?,'42','READY',now(),now())",order,location,reference);
        owner.update("INSERT INTO order_history(order_id,status,created_at) VALUES(?,'READY',now())",order);
        var retained = owner.queryForMap("SELECT tracking_reference,label,status,created_at,updated_at FROM orders WHERE id=?",order);
        var result = upgrade(checksums[0]);
        assertThat(result.getExitCode()).as(result.getStdout()+result.getStderr()).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM tenants WHERE id=?",Integer.class,tenant)).isEqualTo(1);
        assertThat(owner.queryForMap("SELECT tracking_reference,label,status,created_at,updated_at FROM orders WHERE id=?",order)).isEqualTo(retained);
        assertThat(owner.queryForObject("SELECT count(*) FROM order_history WHERE order_id=?",Integer.class,order)).isEqualTo(1);
        var admin=PostgresTestDatabase.ownerDatabase();
        admin.execute("DROP DATABASE IF EXISTS fresh_test WITH (FORCE)");
        admin.execute("CREATE DATABASE fresh_test OWNER kairos_owner");
        var fresh=new JdbcTemplate(new DriverManagerDataSource(PostgresTestDatabase.adminUrl("fresh_test"),"test","test"));
        fresh.execute("ALTER SCHEMA public OWNER TO kairos_owner");
        fresh.execute("REVOKE TEMPORARY ON DATABASE fresh_test FROM PUBLIC");
        Flyway.configure().dataSource(PostgresTestDatabase.adminUrl("fresh_test"),"kairos_owner","owner-test")
            .placeholders(java.util.Map.of("runtimeUser","kairos_runtime")).locations("classpath:db/migration").load().migrate();
        assertThat(schema(owner)).containsExactlyElementsOf(schema(fresh));
        var url = PostgresTestDatabase.adminUrl("upgrade_test");
        var runtime = new JdbcTemplate(new DriverManagerDataSource(url,"kairos_runtime","runtime-test"));
        assertThat(runtime.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
        Flyway.configure().dataSource(url,"kairos_owner","owner-test").placeholders(java.util.Map.of("runtimeUser","kairos_runtime"))
                .locations("classpath:db/migration").load().validate();
        var second = upgrade(checksums[0]);
        assertThat(second.getExitCode()).isNotZero();
        assertThat(second.getStderr()).contains("Upgrade already applied");
    }

    @Test
    void rejectsUnknownChecksumAndRollsBackInvalidRetainedRelationships() throws Exception {
        var unknown = upgrade("0");
        assertThat(unknown.getExitCode()).isNotZero();
        assertThat(unknown.getStderr()).contains("Unknown Flyway");
        assertThat(owner.queryForObject("SELECT to_regnamespace('kairos_security') IS NULL",Boolean.class)).isTrue();
        var tenant=UUID.randomUUID(); var otherTenant=UUID.randomUUID(); var location=UUID.randomUUID(); var otherLocation=UUID.randomUUID(); var order=UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES(?),(?)",tenant,otherTenant);
        owner.update("INSERT INTO locations(id,tenant_id,name,normalized_name,live_normalized_name) VALUES(?,?,'One','one','one'),(?,?,'Two','two','two')",location,tenant,otherLocation,otherTenant);
        var reference=UUID.randomUUID();
        owner.update("INSERT INTO orders(id,location_id,tracking_reference,label,status,created_at,updated_at) VALUES(?,?,?,'1','READY',now(),now())",order,location,reference);
        owner.update("INSERT INTO order_outbox_events(id,order_id,tenant_id,location_id,tracking_reference,event_type,status,occurred_at,webhook_payload,created_at) VALUES(?,?,?,?,?,'ORDER_READY','READY',now(),'{}',now())",UUID.randomUUID(),order,otherTenant,otherLocation,reference);
        var invalid = upgrade(checksums[0]);
        assertThat(invalid.getExitCode()).isNotZero();
        assertThat(invalid.getStderr()).contains("order_outbox_order_identity_fk");
        assertThat(owner.queryForObject("SELECT to_regnamespace('kairos_security') IS NULL",Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT count(*) FROM order_outbox_events",Integer.class)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT checksum FROM flyway_schema_history",Integer.class)).isEqualTo(Integer.valueOf(checksums[0]));
        owner.update("DELETE FROM order_outbox_events");
        var integration=UUID.randomUUID();
        owner.update("INSERT INTO external_integrations(id,tenant_id,name,normalized_name,status,created_at,updated_at,last_enabled_at) VALUES(?,?,'Other','other','ENABLED',now(),now(),now())",integration,otherTenant);
        owner.update("UPDATE orders SET external_integration_id=?,external_idempotency_key='upgrade-test',external_request_fingerprint='fingerprint' WHERE id=?",integration,order);
        var mismatchedTenant=upgrade(checksums[0]);
        assertThat(mismatchedTenant.getExitCode()).isNotZero();
        assertThat(mismatchedTenant.getStderr()).contains("Order integration tenant mismatch");
        assertThat(owner.queryForObject("SELECT to_regnamespace('kairos_security') IS NULL",Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT checksum FROM flyway_schema_history",Integer.class)).isEqualTo(Integer.valueOf(checksums[0]));

    }

    private static java.util.List<String> schema(JdbcTemplate database) {
        return database.queryForList("""
            SELECT definition FROM (
                SELECT 'C:' || c.relname || ':' || a.attname || ':' || format_type(a.atttypid,a.atttypmod)
                    || ':' || a.attnotnull::text || ':' || a.attidentity::text || ':' || COALESCE(pg_get_expr(d.adbin,d.adrelid),'') AS definition
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid
                LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
                WHERE n.nspname='public' AND c.relkind='r' AND c.relname<>'flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped
                UNION ALL SELECT 'K:' || c.relname || ':' || k.conname || ':' || pg_get_constraintdef(k.oid)
                FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
                UNION ALL SELECT 'I:' || pg_get_indexdef(i.indexrelid) FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
                UNION ALL SELECT 'P:' || c.relname || ':' || p.polcmd::text || ':' || COALESCE(pg_get_expr(p.polqual,p.polrelid),'')
                    || ':' || COALESCE(pg_get_expr(p.polwithcheck,p.polrelid),'') FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid
                UNION ALL SELECT 'F:' || pg_get_functiondef(p.oid) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname IN ('public','kairos_security')
                UNION ALL SELECT 'O:' || c.relname || ':' || pg_get_userbyid(c.relowner) || ':' || c.relrowsecurity::text
                    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r'
            ) definitions ORDER BY definition
            """,String.class).stream().map(value -> value.replaceAll("\\s+"," ")).toList();
    }

    private org.testcontainers.containers.Container.ExecResult upgrade(String oldChecksum) throws Exception {
        return PostgresTestDatabase.psql(directory,"-d","upgrade_test","-v","ON_ERROR_STOP=1","-v","ownerUser=kairos_owner",
                "-v","runtimeUser=kairos_runtime","-v","oldChecksum="+oldChecksum,"-v","newChecksum="+checksums[1],"-f","/tmp/rls-upgrade/upgrade_rls.sql");
    }
}
