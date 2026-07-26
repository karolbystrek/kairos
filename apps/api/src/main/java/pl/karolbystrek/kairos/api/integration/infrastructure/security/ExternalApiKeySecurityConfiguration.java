package pl.karolbystrek.kairos.api.integration.infrastructure.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExternalApiKeySecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain externalApiKeySecurityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationProvider authenticationProvider,
            ExternalApiKeyProblemDetailsHandler problemDetailsHandler
    ) throws Exception {
        var authenticationManager = new ProviderManager(authenticationProvider);
        // The default converter accepts the Authorization header only; form and query credentials are disabled.
        var authenticationFilter = new BearerTokenAuthenticationFilter(authenticationManager);
        authenticationFilter.setAuthenticationEntryPoint(problemDetailsHandler);

        return http
                .securityMatcher("/external/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler))
                .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
