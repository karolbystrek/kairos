package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kairos.runtime-mode", havingValue = "worker")
class WorkerEndpointIsolationConfiguration {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> workerEndpointIsolationFilter() {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                var actuatorPrefix = request.getContextPath() + "/actuator/";
                if (request.getRequestURI().startsWith(actuatorPrefix)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
