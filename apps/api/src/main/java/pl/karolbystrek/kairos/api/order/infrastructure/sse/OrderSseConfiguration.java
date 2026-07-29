package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Configuration(proxyBeanMethods = false)
class OrderSseConfiguration {

    @Bean
    OrderSseEmitterRegistry orderSseEmitterRegistry() {
        return new OrderSseEmitterRegistry(SseEmitter::new);
    }
}
