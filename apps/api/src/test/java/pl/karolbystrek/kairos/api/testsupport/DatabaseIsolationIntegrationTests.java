package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DatabaseIsolationIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired JdbcTemplate runtime;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext context;
    @Autowired pl.karolbystrek.kairos.api.order.application.OrderService orders;
    @Autowired pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository repository;


    @Test
    void runtimeCannotBypassIsolationOrReadUnscopedTenantRows() {
        var owner = PostgresTestDatabase.ownerDatabase();
        var id = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES (?)", id);
        try {
            assertThat(runtime.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
            assertThat(runtime.queryForObject("SELECT count(*) FROM tenants WHERE id=?", Integer.class, id)).isZero();
            assertThat(runtime.update("DELETE FROM tenants WHERE id=?", id)).isZero();
            assertThatThrownBy(() -> runtime.update("INSERT INTO tenants(id) VALUES (?)", UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            owner.update("DELETE FROM tenants WHERE id=?", id);
        }
    }

    @Test
    void scopesDirectRepositoriesAndDerivedRowsAndRejectsOwnershipWrites() {
        var fixture = new pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture(PostgresTestDatabase.ownerDatabase());
        var own = fixture.createTenant(); var other = fixture.createTenant();
        var first = orders.createOrder(own.administrator(),own.firstLocationId(),null);
        var second = orders.createOrder(own.administrator(),own.secondLocationId(),null);
        var foreign = orders.createOrder(other.administrator(),other.firstLocationId(),null);
        assertThat(repository.findAll()).isEmpty();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            context.staff(own.manager());
            assertThat(repository.findAll()).extracting(pl.karolbystrek.kairos.api.order.domain.CustomerOrder::getId).containsExactly(first.id());
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_history",Integer.class)).isEqualTo(1);
            assertThat(runtime.update("UPDATE orders SET status='READY' WHERE id=?",foreign.id())).isZero();
            assertThat(runtime.update("DELETE FROM orders WHERE id=?",second.id())).isZero();
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.staff(own.administrator());
            runtime.update("UPDATE locations SET tenant_id=? WHERE id=?",other.tenantId(),own.firstLocationId());
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.staff(own.manager());
            runtime.update("INSERT INTO orders(id,location_id,tracking_reference,label,status,created_at,updated_at) VALUES(?,?,?,'x','READY',now(),now())",
                    UUID.randomUUID(),own.secondLocationId(),UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        transaction.executeWithoutResult(status -> {
            context.trackedOrders(java.util.List.of(first.trackingReference()));
            assertThat(runtime.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_history",Integer.class)).isZero();
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.trackedOrders(java.util.List.of(first.trackingReference()));
            runtime.update("UPDATE orders SET status='READY' WHERE id=?",first.id());
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(PostgresTestDatabase.ownerDatabase().queryForObject("SELECT tenant_id FROM locations WHERE id=?",UUID.class,own.firstLocationId())).isEqualTo(own.tenantId());
        assertThat(PostgresTestDatabase.ownerDatabase().queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> runtime.execute("CREATE TABLE public.shadow(id uuid)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("CREATE TEMP TABLE accounts(id uuid)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("SET ROLE kairos_owner")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(runtime.queryForObject("SELECT has_function_privilege('public', 'public.register_tenant(text,text,timestamptz)', 'EXECUTE')",Boolean.class)).isFalse();
    }

    @Test
    void clearsAuthorityOnSingleConnectionAfterCommitRollbackAndFailedAuthentication() {
        var own = new pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
            pool.setJdbcUrl(System.getProperty("spring.datasource.url"));
            pool.setUsername(System.getProperty("spring.datasource.username"));
            pool.setPassword(System.getProperty("spring.datasource.password"));
            pool.setMaximumPoolSize(1);
            var database = new JdbcTemplate(pool);
            var access = new pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext(database,java.time.Clock.systemUTC());
            var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(pool));
            transaction.executeWithoutResult(status -> {
                access.staff(own.administrator());
                assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isEqualTo(1);
            });
            assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            transaction.executeWithoutResult(status -> {
                access.staff(own.manager());
                assertThat(database.queryForObject("SELECT count(*) FROM locations",Integer.class)).isEqualTo(1);
                status.setRollbackOnly();
            });
            assertThat(database.queryForObject("SELECT count(*) FROM locations",Integer.class)).isZero();
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> access.staff(
                    new pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal(UUID.randomUUID(),own.tenantId(),pl.karolbystrek.kairos.api.account.domain.TenantRole.ADMIN))))
                .isInstanceOf(pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException.class);
            assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            transaction.executeWithoutResult(status -> {
                database.queryForObject("SELECT set_config('kairos.scope','staff',true),set_config('kairos.identity','invalid',true)",(rs,n)->rs.getString(1));
                assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            });
        }
    }

    @Test
    void refusesJoinedAuthorityChangesAndRestoresOuterScopeAfterRequiresNew() {
        var own = new pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var inner = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            context.staff(own.manager());
            assertThatThrownBy(() -> context.staff(own.administrator())).isInstanceOf(IllegalStateException.class);
            inner.executeWithoutResult(innerStatus -> {
                assertThat(runtime.queryForObject("SELECT count(*) FROM locations",Integer.class)).isZero();
                context.staff(own.administrator());
                assertThat(runtime.queryForObject("SELECT count(*) FROM locations",Integer.class)).isEqualTo(2);
            });
            assertThat(runtime.queryForObject("SELECT count(*) FROM locations",Integer.class)).isEqualTo(1);
        });
        assertThat(runtime.queryForObject("SELECT count(*) FROM locations",Integer.class)).isZero();
    }

    @Test
    void startupRejectsOwnerConnectionsAndIncompletePolicies() {
        var owner=PostgresTestDatabase.ownerDatabase();
        assertThatThrownBy(() -> new pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseIsolationVerifier(owner).run(null)).isInstanceOf(IllegalStateException.class);
        owner.execute("REVOKE UPDATE ON orders FROM kairos_runtime");
        try {
            assertThatThrownBy(() -> new pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseIsolationVerifier(runtime).run(null)).isInstanceOf(IllegalStateException.class);
        } finally {
            owner.execute("GRANT UPDATE ON orders TO kairos_runtime");
        }
        owner.execute("DROP POLICY scoped_insert ON orders");
        try {
            assertThatThrownBy(() -> new pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseIsolationVerifier(runtime).run(null)).isInstanceOf(IllegalStateException.class);
        } finally {
            owner.execute("CREATE POLICY scoped_insert ON orders FOR INSERT WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true))");
        }
    }
}
