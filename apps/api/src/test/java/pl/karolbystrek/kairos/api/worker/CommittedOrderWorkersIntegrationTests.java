package pl.karolbystrek.kairos.api.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryClaimService;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookDeliveryProcessor;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookOutboxFanoutService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryClaimService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryProcessor;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushOutboxFanoutService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(CommittedOrderWorkersIntegrationTests.ConfigurationForTest.class)
class CommittedOrderWorkersIntegrationTests {
    @Resource private ApplicationEventPublisher events;
    @Resource private PlatformTransactionManager transactions;
    @Resource private CustomerPushOutboxFanoutService pushFanout;
    @Resource private CustomerPushDeliveryClaimService pushClaims;
    @Resource private WebhookOutboxFanoutService webhookFanout;
    @Resource private WebhookDeliveryClaimService webhookClaims;

    @BeforeEach
    void resetInteractions() {
        reset(pushFanout, pushClaims, webhookFanout, webhookClaims);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"IN_PREPARATION", "READY"})
    void committedCreationAndStatusChangesWakeBothPipelinesAfterCommit(OrderStatus status) {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            events.publishEvent(event(status));
            verifyNoInteractions(pushFanout, pushClaims, webhookFanout, webhookClaims);
        });
        verify(pushClaims, timeout(2000)).claimAvailable();
        verify(webhookClaims, timeout(2000)).claimAvailable();
        var pushOrder = inOrder(pushFanout, pushClaims);
        pushOrder.verify(pushFanout).fanOutAvailable();
        pushOrder.verify(pushClaims).claimAvailable();
        var webhookOrder = inOrder(webhookFanout, webhookClaims);
        webhookOrder.verify(webhookFanout).fanOutAvailable();
        webhookOrder.verify(webhookClaims).claimAvailable();
    }

    @Test
    void rollbackDoesNotWakeEitherPipeline() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            events.publishEvent(event(OrderStatus.READY));
            transaction.setRollbackOnly();
        });
        verifyNoInteractions(pushFanout, pushClaims, webhookFanout, webhookClaims);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedFanoutOrDeliveryStopsDrainingUntilTheNextWake(boolean failFanout) {
        if (failFanout) {
            when(pushFanout.fanOutAvailable()).thenThrow(new IllegalStateException("fanout unavailable"));
            when(webhookFanout.fanOutAvailable()).thenThrow(new IllegalStateException("fanout unavailable"));
        } else {
            when(pushClaims.claimAvailable()).thenThrow(new IllegalStateException("delivery unavailable"));
            when(webhookClaims.claimAvailable()).thenThrow(new IllegalStateException("delivery unavailable"));
        }
        new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                events.publishEvent(event(OrderStatus.READY)));
        verify(pushClaims, after(100).times(1)).claimAvailable();
        verify(webhookClaims, after(100).times(1)).claimAvailable();
        reset(pushFanout, pushClaims, webhookFanout, webhookClaims);
        new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                events.publishEvent(event(OrderStatus.READY)));
        verify(pushClaims, timeout(2000)).claimAvailable();
        verify(webhookClaims, timeout(2000)).claimAvailable();
    }

    private OrderStatusChangedEvent event(OrderStatus status) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), status,
                Instant.now(), 42L, "Order", null, UUID.randomUUID(), UUID.randomUUID());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ComponentScan(basePackages = {
            "pl.karolbystrek.kairos.api.notification.infrastructure.worker",
            "pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker"
    }, useDefaultFilters = false, includeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ".*\\.(CustomerPush|Webhook)(Worker|OutboxFanoutScheduler|DeliveryScheduler)"
    ))
    static class ConfigurationForTest {
        @Bean PlatformTransactionManager transactions() {
            return new JdbcTransactionManager(PostgresTestDatabase.ownerDatabase().getDataSource());
        }
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }
        @Bean CustomerPushOutboxFanoutService pushFanout() { return mock(CustomerPushOutboxFanoutService.class); }
        @Bean CustomerPushDeliveryClaimService pushClaims() { return mock(CustomerPushDeliveryClaimService.class); }
        @Bean CustomerPushDeliveryProcessor pushProcessor() { return mock(CustomerPushDeliveryProcessor.class); }
        @Bean WebhookOutboxFanoutService webhookFanout() { return mock(WebhookOutboxFanoutService.class); }
        @Bean WebhookDeliveryClaimService webhookClaims() { return mock(WebhookDeliveryClaimService.class); }
        @Bean WebhookDeliveryProcessor webhookProcessor() { return mock(WebhookDeliveryProcessor.class); }
    }
}
