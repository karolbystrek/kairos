package pl.karolbystrek.kairos.api.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.authentication.application.OneTimeBearerTokenService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseIsolationVerifier;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DatabaseIsolationIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired JdbcTemplate runtime;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AccountInvitationService invitations;
    @Autowired OneTimeBearerTokenService tokens;
    @Autowired DatabaseAccessContext context;
    @Autowired OrderService orders;
    @Autowired CustomerOrderRepository repository;


    @Test
    void runtimeCannotBypassIsolationOrReadUnscopedTenantRows() {
        var owner = PostgresTestDatabase.ownerDatabase();
        var id = UUID.randomUUID();
        owner.update("INSERT INTO tenants(id) VALUES (?)", id);
        try {
            assertThat(runtime.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
            assertThat(runtime.queryForObject("SELECT count(*) FROM tenants WHERE id=?", Integer.class, id)).isZero();
            assertThat(runtime.update("DELETE FROM tenants WHERE id=?", id)).isZero();
            assertThatThrownBy(() -> runtime.update("INSERT INTO tenants(id) VALUES (?)", UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        } finally {
            owner.update("DELETE FROM tenants WHERE id=?", id);
        }
    }

    @Test
    void scopesDirectRepositoriesAndDerivedRowsAndRejectsOwnershipWrites() {
        var fixture = new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase());
        var own = fixture.createTenant(); var other = fixture.createTenant();
        var first = orders.createOrder(own.administrator(),own.firstLocationId(),null);
        var second = orders.createOrder(own.administrator(),own.secondLocationId(),null);
        var foreign = orders.createOrder(other.administrator(),other.firstLocationId(),null);
        assertThat(repository.findAll()).isEmpty();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            context.staff(own.manager());
            assertThat(repository.findAll()).extracting(CustomerOrder::getId).containsExactly(first.id());
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_history",Integer.class)).isEqualTo(1);
            assertThat(runtime.update("UPDATE orders SET status='READY' WHERE id=?",foreign.id())).isZero();
            assertThat(runtime.update("DELETE FROM orders WHERE id=?",second.id())).isZero();
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.staff(own.administrator());
            runtime.update("UPDATE locations SET tenant_id=? WHERE id=?",other.tenantId(),own.firstLocationId());
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.staff(own.manager());
            runtime.update("INSERT INTO orders(id,location_id,tracking_reference,label,status,created_at,updated_at) VALUES(?,?,?,'x','READY',now(),now())",
                    UUID.randomUUID(),own.secondLocationId(),UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
        transaction.executeWithoutResult(status -> {
            context.trackedOrders(List.of(first.trackingReference()));
            assertThat(runtime.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
            assertThat(runtime.queryForObject("SELECT count(*) FROM order_history",Integer.class)).isZero();
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.trackedOrders(List.of(first.trackingReference()));
            runtime.update("UPDATE orders SET status='READY' WHERE id=?",first.id());
        })).isInstanceOf(DataAccessException.class);
        assertThat(PostgresTestDatabase.ownerDatabase().queryForObject("SELECT tenant_id FROM locations WHERE id=?",UUID.class,own.firstLocationId())).isEqualTo(own.tenantId());
        assertThat(PostgresTestDatabase.ownerDatabase().queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> runtime.execute("CREATE TABLE public.shadow(id uuid)")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("CREATE TEMP TABLE accounts(id uuid)")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("SET ROLE kairos_owner")).isInstanceOf(DataAccessException.class);
        assertThat(runtime.queryForObject("SELECT has_function_privilege('public', 'public.staff_authentication(text)', 'EXECUTE')",Boolean.class)).isFalse();
    }

    @Test
    void registrationScopeAllowsOnlyItsNewTenantAndAdministrator() {
        var tenant=UUID.randomUUID(); var account=UUID.randomUUID();
        var transaction=new TransactionTemplate(transactionManager);
        assertThatNoException().isThrownBy(() -> transaction.executeWithoutResult(status -> {
            runtime.queryForMap("SELECT set_config('kairos.scope','registration',true),set_config('kairos.identity',?,true),set_config('kairos.references',?,true)",tenant.toString(),account.toString());
            runtime.update("INSERT INTO tenants(id) VALUES(?)",tenant);
            runtime.update("INSERT INTO accounts(id,tenant_id,email,provider_subject,tenant_role,status,created_at,updated_at) VALUES(?,?,'registered@example.com','registered','ADMIN','ENABLED',now(),now())",account,tenant);
            assertThat(runtime.queryForObject("SELECT count(*) FROM accounts",Integer.class)).isEqualTo(1);
        }));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            runtime.queryForMap("SELECT set_config('kairos.scope','registration',true),set_config('kairos.identity',?,true),set_config('kairos.references',?,true)",tenant.toString(),account.toString());
            runtime.update("INSERT INTO accounts(id,tenant_id,email,provider_subject,tenant_role,status,created_at,updated_at) VALUES(?,?,'unrelated@example.com','unrelated','ADMIN','ENABLED',now(),now())",UUID.randomUUID(),tenant);
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            runtime.queryForMap("SELECT set_config('kairos.scope','registration',true),set_config('kairos.identity',?,true)",tenant.toString());
            runtime.update("INSERT INTO tenants(id) VALUES(?)",UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
        assertThat(runtime.queryForObject("SELECT count(*) FROM accounts",Integer.class)).isZero();
    }

    @Test
    void invitationScopeCannotBrowseOrChangeUnrelatedAccountsAndInvitations() {
        var fixture=new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase());
        var own=fixture.createTenant(); var other=fixture.createTenant();
        var invitation=invitations.create(own.administrator(),own.firstLocationId(),AssignmentRole.OPERATOR);
        invitations.create(other.administrator(),other.firstLocationId(),AssignmentRole.OPERATOR);
        var transaction=new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            context.invitation(tokens.hash(invitation.token()),UUID.randomUUID());
            assertThat(runtime.queryForObject("SELECT count(*) FROM account_invitations",Integer.class)).isEqualTo(1);
            assertThat(runtime.queryForObject("SELECT count(*) FROM locations",Integer.class)).isEqualTo(1);
            assertThat(runtime.queryForObject("SELECT count(*) FROM accounts",Integer.class)).isEqualTo(1);
            assertThat(runtime.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
            assertThat(runtime.update("UPDATE accounts SET status='DISABLED' WHERE id=?",own.administrator().accountId())).isZero();
            assertThat(runtime.update("UPDATE account_invitations SET state='REVOKED',revocation_reason='STAFF_REVOKED',revoked_at=now() WHERE tenant_id=?",other.tenantId())).isZero();
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            context.invitation(tokens.hash(invitation.token()),UUID.randomUUID());
            runtime.update("UPDATE account_invitations SET state='REVOKED',revocation_reason='STAFF_REVOKED',revoked_at=now() WHERE id=?",invitation.invitation().id());
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void clearsAuthorityOnSingleConnectionAfterCommitRollbackAndFailedAuthentication() {
        var own = new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl(System.getProperty("spring.datasource.url"));
            pool.setUsername(System.getProperty("spring.datasource.username"));
            pool.setPassword(System.getProperty("spring.datasource.password"));
            pool.setMaximumPoolSize(1);
            var database = new JdbcTemplate(pool);
            var access = new DatabaseAccessContext(database,Clock.systemUTC());
            var transaction = new TransactionTemplate(new JdbcTransactionManager(pool));
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
                    new StaffPrincipal(UUID.randomUUID(),own.tenantId(),TenantRole.ADMIN))))
                .isInstanceOf(StaffAccessDeniedException.class);
            assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            transaction.executeWithoutResult(status -> {
                database.queryForObject("SELECT set_config('kairos.scope','staff',true),set_config('kairos.identity','invalid',true)",(rs,n)->rs.getString(1));
                assertThat(database.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            });
        }
    }

    @Test
    void refusesJoinedAuthorityChangesAndRestoresOuterScopeAfterRequiresNew() {
        var own = new IntegrationTestFixture(PostgresTestDatabase.ownerDatabase()).createTenant();
        var transaction = new TransactionTemplate(transactionManager);
        var inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        assertThatThrownBy(() -> new DatabaseIsolationVerifier(owner).run(null)).isInstanceOf(IllegalStateException.class);
        owner.execute("REVOKE UPDATE ON orders FROM kairos_runtime");
        try {
            assertThatThrownBy(() -> new DatabaseIsolationVerifier(runtime).run(null)).isInstanceOf(IllegalStateException.class);
        } finally {
            owner.execute("GRANT UPDATE ON orders TO kairos_runtime");
        }
        owner.execute("DROP POLICY scoped_insert ON orders");
        try {
            assertThatThrownBy(() -> new DatabaseIsolationVerifier(runtime).run(null)).isInstanceOf(IllegalStateException.class);
        } finally {
            owner.execute("CREATE POLICY scoped_insert ON orders FOR INSERT WITH CHECK (kairos_security.staff_location(location_id) OR kairos_security.integration_location(location_id,true))");
        }
    }
}
