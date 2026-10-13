package pl.karolbystrek.kairos.api.analytics.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.analytics.application.AnalyticsReportService.Range;
import pl.karolbystrek.kairos.api.authentication.application.CurrentAccountService;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture.TenantFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableTestClockConfiguration.class)
class AnalyticsReportIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    private static final Range WEEK = new Range(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"), "Europe/Warsaw");
    private static final String HOUR = "2026-10-30T10:00:00Z";
    @Autowired private AnalyticsReportService reports;
    @Autowired private CurrentAccountService accounts;
    @Autowired private DatabaseAccessContext access;
    @Autowired private MutableTestClock clock;
    @Autowired private JdbcTemplate runtime;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MockMvc mvc;
    private final JdbcTemplate owner = PostgresTestDatabase.ownerDatabase();
    private TenantFixture tenant;
    private TenantFixture other;

    @BeforeEach
    void fixture() {
        clock.setInstant(Instant.parse("2026-11-01T12:00:00Z"));
        tenant = new IntegrationTestFixture(owner).createTenant();
        other = new IntegrationTestFixture(owner).createTenant();
    }

    @Test
    void scopesReportsByRoleInApplicationAndDatabase() {
        order(tenant, tenant.firstLocationId(), HOUR, "IN_PREPARATION", 2, 0, 0);
        order(tenant, tenant.secondLocationId(), HOUR, "IN_PREPARATION", 3, 0, 0);
        order(other, other.firstLocationId(), HOUR, "IN_PREPARATION", 7, 0, 0);
        usage(tenant, null, "menu_editor_opened", 4);
        usage(tenant, tenant.firstLocationId(), "queue_opened", 5);
        usage(tenant, tenant.firstLocationId(), "review_link_clicked", 6);

        var tenantReport = reports.restaurant(tenant.administrator(), WEEK, null);
        assertThat(tenantReport.transitions()).containsEntry("IN_PREPARATION", 5L);
        assertThat(tenantReport.actions()).containsOnlyKeys("review_link_clicked", "review_prompt_dismissed")
                .containsEntry("review_link_clicked", 6L);
        assertThat(tenantReport.locations()).extracting(AnalyticsReportService.LocationTotals::locationId)
                .containsExactlyInAnyOrder(tenant.firstLocationId(), tenant.secondLocationId());
        var locationReport = reports.restaurant(tenant.administrator(), WEEK, tenant.secondLocationId());
        assertThat(locationReport.transitions()).containsEntry("IN_PREPARATION", 3L);
        assertThat(locationReport.actions().values()).containsOnly(0L);
        assertThat(locationReport.locations()).isNull();
        var managerReport = reports.restaurant(tenant.manager(), WEEK, null);
        assertThat(managerReport.transitions()).containsEntry("IN_PREPARATION", 2L);
        assertThat(managerReport.actions()).containsOnlyKeys("review_link_clicked", "review_prompt_dismissed")
                .containsEntry("review_link_clicked", 6L);
        assertThat(locationReport.actions()).containsOnlyKeys("review_link_clicked", "review_prompt_dismissed");
        owner.update("INSERT INTO platform_administrators(account_id) VALUES (?)", tenant.administrator().accountId());
        assertThat(reports.platform(tenant.administrator(), WEEK).actions())
                .containsEntry("menu_editor_opened", 4L).containsEntry("queue_opened", 5L)
                .containsEntry("review_link_clicked", 6L);

        assertThatThrownBy(() -> reports.restaurant(tenant.manager(), WEEK, tenant.secondLocationId()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> reports.restaurant(tenant.administrator(), WEEK, other.firstLocationId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(visibleLocations(tenant.manager())).containsExactly(tenant.firstLocationId());
        assertThat(visibleLocations(tenant.administrator()))
                .containsExactlyInAnyOrder(tenant.firstLocationId(), tenant.secondLocationId());
        assertThat(accounts.get(tenant.manager()).capabilities()).contains("VIEW_ANALYTICS");

        owner.update("DELETE FROM location_assignments WHERE account_id=?", tenant.manager().accountId());
        owner.update("""
                INSERT INTO location_assignments(account_id, location_id, tenant_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'OPERATOR', now(), now())
                """, tenant.manager().accountId(), tenant.firstLocationId(), tenant.tenantId());
        assertThatThrownBy(() -> reports.restaurant(tenant.manager(), WEEK, null)).isInstanceOf(StaffAccessDeniedException.class);
        assertThat(visibleLocations(tenant.manager())).isEmpty();
        assertThat(accounts.get(tenant.manager()).capabilities()).doesNotContain("VIEW_ANALYTICS");
    }

    @Test
    void mergesDurationsAndAssignsWholeHoursByLocalStartDate() {
        var location = tenant.firstLocationId();
        order(tenant, location, "2026-10-24T21:00:00Z", "READY", 10, 10, 10);   // 23:00 CEST, previous day
        order(tenant, location, "2026-10-24T22:00:00Z", "READY", 1, 1, 60_000);  // 00:00 CEST
        order(tenant, location, "2026-10-25T22:00:00Z", "READY", 3, 3, 30_000); // 23:00 CET of the 25-hour day
        order(tenant, location, "2026-10-25T23:00:00Z", "COMPLETED", 1, 0, 0);   // next local midnight
        var dst = reports.restaurant(tenant.administrator(),
                new Range(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-26"), "Europe/Warsaw"), null);
        assertThat(dst.days()).hasSize(2);
        assertThat(dst.days().get(0).transitions()).containsEntry("READY", 4L).containsEntry("COMPLETED", 0L);
        assertThat(dst.days().get(1).transitions()).containsEntry("COMPLETED", 1L);
        assertThat(dst.preparation().samples()).isEqualTo(4);
        assertThat(dst.preparation().meanMs()).isEqualTo(22_500.0);
        assertThat(dst.collection().samples()).isZero();
        assertThat(dst.collection().meanMs()).isNull();
        assertThat(dst.partialDay()).isNull();

        // +05:30 places UTC hours 18:00 and 19:00 on either side of local midnight.
        order(tenant, location, "2026-10-29T18:00:00Z", "IN_PREPARATION", 1, 0, 0);
        order(tenant, location, "2026-10-29T19:00:00Z", "IN_PREPARATION", 2, 0, 0);
        order(tenant, location, "2026-10-30T18:00:00Z", "IN_PREPARATION", 4, 0, 0);
        order(tenant, location, "2026-10-30T19:00:00Z", "IN_PREPARATION", 8, 0, 0);
        var kolkata = reports.restaurant(tenant.administrator(),
                new Range(LocalDate.parse("2026-10-30"), LocalDate.parse("2026-10-30"), "Asia/Kolkata"), null);
        assertThat(kolkata.transitions()).containsEntry("IN_PREPARATION", 6L);

        var today = reports.restaurant(tenant.administrator(), WEEK, null);
        assertThat(today.partialDay()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(today.days()).hasSize(7);
        for (var range : List.of(new Range(LocalDate.parse("2026-08-03"), LocalDate.parse("2026-11-01"), "UTC"),
                new Range(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), "UTC"),
                new Range(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-10-31"), "UTC"),
                new Range(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), "+02:00"),
                new Range(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), "Mars/Olympus"))) {
            assertThatThrownBy(() -> reports.restaurant(tenant.administrator(), range, null))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        assertThat(reports.restaurant(tenant.administrator(),
                new Range(LocalDate.parse("2026-08-04"), LocalDate.parse("2026-11-01"), "UTC"), null).days()).hasSize(90);
    }

    @Test
    void keepsPurgedLocationsDistinctWithoutRetainedNames() {
        order(tenant, tenant.firstLocationId(), HOUR, "COMPLETED", 2, 0, 0);
        order(tenant, tenant.secondLocationId(), HOUR, "COMPLETED", 3, 0, 0);
        owner.update("DELETE FROM location_assignments WHERE tenant_id=?", tenant.tenantId());
        owner.update("DELETE FROM locations WHERE tenant_id=?", tenant.tenantId());

        var report = reports.restaurant(tenant.administrator(), WEEK, null);
        assertThat(report.locations()).hasSize(2).allSatisfy(location -> assertThat(location.name()).isNull());
        assertThat(report.locations()).extracting(location -> location.transitions().get("COMPLETED"))
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(report.firstRecordedHour()).isEqualTo(Instant.parse(HOUR));
    }

    @Test
    void platformAuthorityIsManualRevocableAndExcludesDeletedTenants() throws Exception {
        order(tenant, tenant.firstLocationId(), HOUR, "IN_PREPARATION", 2, 0, 0);
        order(other, other.firstLocationId(), HOUR, "IN_PREPARATION", 7, 0, 0);
        var administrator = tenant.administrator();
        assertThatThrownBy(() -> reports.platform(administrator, WEEK)).isInstanceOf(StaffAccessDeniedException.class);
        assertThat(accounts.get(administrator).capabilities()).doesNotContain("VIEW_PLATFORM_ANALYTICS");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            access.staff(administrator);
            runtime.update("INSERT INTO platform_administrators(account_id) VALUES (?)", administrator.accountId());
        })).isInstanceOf(DataAccessException.class);

        owner.update("INSERT INTO platform_administrators(account_id) VALUES (?)", administrator.accountId());
        assertThat(accounts.get(administrator).capabilities()).contains("VIEW_PLATFORM_ANALYTICS");
        var platform = reports.platform(administrator, WEEK);
        assertThat(platform.transitions()).containsEntry("IN_PREPARATION", 9L);
        assertThat(platform.locations()).isNull();
        mvc.perform(get("/api/platform-analytics/v1/health").contextPath("/api").secure(true).with(as(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.redis").value(not("UP")))
                .andExpect(jsonPath("$.workers").isArray())
                .andExpect(content().string(not(containsString(tenant.tenantId().toString()))));
        mvc.perform(get("/api/platform-analytics/v1/health").contextPath("/api").secure(true).with(as(tenant.manager())))
                .andExpect(status().isForbidden());

        owner.update("DELETE FROM location_assignments WHERE tenant_id=?", other.tenantId());
        owner.update("DELETE FROM accounts WHERE tenant_id=?", other.tenantId());
        owner.update("DELETE FROM locations WHERE tenant_id=?", other.tenantId());
        owner.update("DELETE FROM tenants WHERE id=?", other.tenantId());
        assertThat(reports.platform(administrator, WEEK).transitions()).containsEntry("IN_PREPARATION", 2L);

        owner.update("DELETE FROM platform_administrators WHERE account_id=?", administrator.accountId());
        assertThatThrownBy(() -> reports.platform(administrator, WEEK)).isInstanceOf(StaffAccessDeniedException.class);
        mvc.perform(get("/api/platform-analytics/v1/health").contextPath("/api").secure(true).with(as(administrator)))
                .andExpect(status().isForbidden());
    }

    private List<UUID> visibleLocations(StaffPrincipal principal) {
        return new TransactionTemplate(transactions).execute(status -> {
            access.staff(principal);
            return runtime.queryForList("SELECT DISTINCT location_id FROM order_hourly_reports", UUID.class);
        });
    }

    private static RequestPostProcessor as(StaffPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"))));
    }

    private void order(TenantFixture owner, UUID location, String hour, String status, long transitions, long samples,
                       long durationMs) {
        this.owner.update("""
                INSERT INTO order_hourly_analytics (tenant_id, location_id, hour, status, bucket_version, transition_count,
                    duration_count, total_duration_ms, bucket_0, bucket_1, bucket_2, bucket_3, bucket_4, bucket_5, bucket_6, bucket_7)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0)
                """, owner.tenantId(), location, Timestamp.from(Instant.parse(hour)), status, transitions, samples,
                durationMs, samples);
    }

    private void usage(TenantFixture owner, UUID location, String event, long actions) {
        this.owner.update("INSERT INTO usage_hourly_analytics (tenant_id, location_id, hour, event, action_count) VALUES (?, ?, ?, ?, ?)",
                owner.tenantId(), location, Timestamp.from(Instant.parse(HOUR)), event, actions);
    }
}
