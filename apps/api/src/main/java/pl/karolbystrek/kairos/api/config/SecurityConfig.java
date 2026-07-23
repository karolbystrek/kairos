package pl.karolbystrek.kairos.api.config;

import jakarta.servlet.DispatcherType;
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
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.StaffPrincipalJwtAuthenticationConverter;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CookieBearerTokenResolver;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SecurityProblemDetailsHandler;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.SpaCsrfTokenRequestHandler;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_COOKIE;
import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_HEADER;

@Configuration
@EnableMethodSecurity
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
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SpaCsrfTokenRequestHandler csrfTokenRequestHandler,
            CookieBearerTokenResolver bearerTokenResolver,
            StaffPrincipalJwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemDetailsHandler problemDetailsHandler
    ) {
        return http
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
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/auth/csrf",
                                "/tracked-orders/**",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/auth/login",
                                "/auth/refresh"
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
