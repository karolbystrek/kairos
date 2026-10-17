package pl.karolbystrek.kairos.api.email.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.email.infrastructure.config.EmailProperties;
import pl.karolbystrek.kairos.api.email.infrastructure.security.EmailPayloadCipher;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(CommittedEmailWorkerIntegrationTests.ConfigurationForTest.class)
class CommittedEmailWorkerIntegrationTests {
    @Resource private EmailDeliveryService deliveries;
    @Resource private EmailDeliveryWorker delivery;
    @Resource private EmailWorker worker;
    @Resource private PlatformTransactionManager transactions;

    @BeforeEach
    void resetDelivery() {
        reset(delivery);
    }

    @Test
    void enqueueWakesDeliveryOnlyAfterCommitWithoutWaitingForAPoll() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            deliveries.enqueue("owner@example.com", "Verify", "123456");
            verifyNoInteractions(delivery);
        });
        verify(delivery, timeout(2000)).deliverAvailable();
    }

    @Test
    void rolledBackEnqueueDoesNotWakeDelivery() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            deliveries.enqueue("owner@example.com", "Verify", "123456");
            transaction.setRollbackOnly();
        });
        verifyNoInteractions(delivery);
    }

    @Test
    void recoveryDrainsAvailableBatchesWithoutAnEnqueue() {
        when(delivery.deliverAvailable()).thenReturn(true, false);
        worker.recover();
        verify(delivery, timeout(2000).times(2)).deliverAvailable();
    }

    @Test
    void deliveryFailureStopsDrainingUntilRecovery() {
        when(delivery.deliverAvailable()).thenThrow(new IllegalStateException("temporarily unavailable"))
                .thenReturn(false);
        worker.recover();
        verify(delivery, after(100).times(1)).deliverAvailable();
        worker.recover();
        verify(delivery, timeout(2000).times(2)).deliverAvailable();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ComponentScan(basePackageClasses = EmailDeliveryService.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = ".*\\.(EmailDeliveryService|EmailWorker)"))
    @Import(WorkerCycles.class)
    static class ConfigurationForTest {
        @Bean PlatformTransactionManager transactions() {
            return new JdbcTransactionManager(PostgresTestDatabase.ownerDatabase().getDataSource());
        }
        @Bean JdbcTemplate database() { return mock(JdbcTemplate.class); }
        @Bean EmailDeliveryWorker delivery() { return mock(EmailDeliveryWorker.class); }
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean EmailProperties properties() {
            return new EmailProperties(EmailProperties.Mode.LOG, "", "", "Kairos <no-reply@example.com>");
        }
        @Bean EmailPayloadCipher cipher() {
            return EmailPayloadCipher.from(new ClassPathResource("keys/test-email-encryption.bin"));
        }
    }
}
