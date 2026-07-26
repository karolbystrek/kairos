package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "kairos.runtime-mode", havingValue = "worker")
class WebhookWorkerConfiguration {
}
