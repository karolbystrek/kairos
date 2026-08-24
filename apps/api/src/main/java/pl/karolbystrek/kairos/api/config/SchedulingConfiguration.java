package pl.karolbystrek.kairos.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "kairos.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration {
}
