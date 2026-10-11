package pl.karolbystrek.kairos.api.retention.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RetentionProperties.class)
class RetentionConfiguration {
}
