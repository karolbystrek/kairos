package pl.karolbystrek.kairos.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.StaffPrincipalJwtAuthenticationConverter;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CookieBearerTokenResolver;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SecurityProblemDetailsHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SpaCsrfTokenRequestHandler;

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
                "PATCH",
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
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SpaCsrfTokenRequestHandler csrfTokenRequestHandler,
            CookieBearerTokenResolver bearerTokenResolver,
            StaffPrincipalJwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemDetailsHandler problemDetailsHandler
    ) {
        return http
                .cors(withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        // Bearer authentication runs on every access-cookie request; it must not rotate CSRF state.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                // Resource Server assumes header bearer tokens are not CSRF-prone. Kairos uses a cookie.
                                // Restore protection for every unsafe method after all configurers have run.
                                filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                return filter;
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true))
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
                                "/auth/v1/refresh",
                                "/tenant-registrations/v1"
                        ).permitAll()
                        .requestMatchers(
                                "/customer-notifications/v1/subscription",
                                "/customer-notifications/v1/subscription-replacement",
                                "/customer-notifications/v1/enrollments"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
