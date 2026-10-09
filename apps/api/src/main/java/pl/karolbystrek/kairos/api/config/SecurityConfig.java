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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.karolbystrek.kairos.api.authentication.application.StaffAuthenticationService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SecurityProblemDetailsHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SpaCsrfTokenRequestHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.StaffSessionFilter;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

import java.util.List;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_COOKIE;
import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_HEADER;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(ApplicationOriginsProperties.class)
public class SecurityConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(ApplicationOriginsProperties properties) {
        var customerConfiguration = corsConfiguration(properties.customer());
        var panelConfiguration = corsConfiguration(properties.panel());
        var sharedConfiguration = corsConfiguration(
                properties.customer(),
                properties.panel()
        );

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/auth/v1/csrf", sharedConfiguration);
        source.registerCorsConfiguration("/tracked-orders/**", customerConfiguration);
        source.registerCorsConfiguration("/customer-notifications/**", customerConfiguration);
        source.registerCorsConfiguration("/auth/**", panelConfiguration);
        source.registerCorsConfiguration("/tenant-registrations/**", panelConfiguration);
        source.registerCorsConfiguration("/locations/**", panelConfiguration);
        source.registerCorsConfiguration("/accounts/**", panelConfiguration);
        source.registerCorsConfiguration("/account-invitations/**", panelConfiguration);
        source.registerCorsConfiguration("/account-invitation-previews/**", panelConfiguration);
        source.registerCorsConfiguration("/account-invitation-redemptions/**", panelConfiguration);
        source.registerCorsConfiguration("/orders/**", panelConfiguration);
        source.registerCorsConfiguration("/external-integrations/**", panelConfiguration);
        source.registerCorsConfiguration("/api-keys/**", panelConfiguration);
        source.registerCorsConfiguration("/api-key-versions/**", panelConfiguration);
        source.registerCorsConfiguration("/webhook-subscriptions/**", panelConfiguration);
        source.registerCorsConfiguration("/webhook-signing-secrets/**", panelConfiguration);
        return source;
    }

    private static CorsConfiguration corsConfiguration(String... allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of(
                "GET",
                "HEAD",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }

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
            ZitadelClient provider,
            StaffAuthenticationService authentication,
            HttpSessionIdResolver cookies,
            SecurityProblemDetailsHandler problemDetailsHandler
    ) {
        return http
                .cors(withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER)
                    .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .addFilterBefore(new StaffSessionFilter(
                    provider, authentication, cookies, problemDetailsHandler), AnonymousAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/auth/v1/csrf",
                                "/tracked-orders/v1/**",
                                "/customer-notifications/v1/configuration",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/auth/v1/login",
                                "/auth/v1/logout",
                                "/tenant-registrations/v1",
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
                                "/auth/v1/logout-all",
                                "/auth/v1/me",
                                "/auth/v1/password"
                        ).authenticated()
                        .requestMatchers(
                                "/locations/v1/**",
                                "/accounts/v1/**",
                                "/account-invitations/v1/**",
                                "/orders/v1/**",
                                "/external-integrations/v1/**",
                                "/api-keys/v1/**",
                                "/api-key-versions/v1/**",
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
