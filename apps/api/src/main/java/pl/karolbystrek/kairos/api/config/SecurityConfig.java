package pl.karolbystrek.kairos.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import pl.karolbystrek.kairos.api.authentication.application.StaffAuthenticationService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SecurityProblemDetailsHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SpaCsrfTokenRequestHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.StaffSessionFilter;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_COOKIE;
import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_HEADER;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(ApplicationOriginsProperties.class)
public class SecurityConfig {

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(true)
                .httpOnly(false)
                .sameSite("Lax")
                .path("/"));
        return repository;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SpaCsrfTokenRequestHandler csrfTokenRequestHandler,
            StaffAuthenticationService authentication,
            HttpSessionIdResolver cookies,
            SecurityProblemDetailsHandler problemDetailsHandler
    ) {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER)
                    .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .addFilterBefore(new StaffSessionFilter(
                    authentication, cookies, problemDetailsHandler), AnonymousAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/auth/v1/csrf",
                                "/auth/v1/verification",
                                "/tracked-orders/v1/**",
                                "/customer-notifications/v1/configuration",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/tracked-orders/v1/*/review-actions",
                                "/auth/v1/login",
                                "/auth/v1/logout",
                                "/tenant-registrations/v1",
                                "/auth/v1/verification",
                                "/auth/v1/verification/resend",
                                "/auth/v1/password-reset",
                                "/auth/v1/password-reset/confirmation",
                                "/account-invitation-previews/v1",
                                "/account-invitation-redemptions/v1"
                        ).permitAll()
                        .requestMatchers(
                                "/customer-notifications/v1/subscription",
                                "/customer-notifications/v1/subscription-replacement",
                                "/customer-notifications/v1/enrollments"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/v1/logout",
                                "/auth/v1/me",
                                "/auth/v1/password"
                        ).authenticated()
                        .requestMatchers(
                                "/locations/v1/**",
                                "/accounts/v1/**",
                                "/account-invitations/v1/**",
                                "/usage-analytics/v1/**",
                                "/analytics/v1/**",
                                "/platform-analytics/v1/**",
                                "/orders/v1/**",
                                "/external-integrations/v1/**",
                                "/api-keys/v1/**",
                                "/webhook-subscriptions/v1/**",
                                "/webhook-signing-secrets/v1/**"
                        ).hasRole("TENANT_ACCOUNT")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler))
                .build();
    }
}
