package pl.karolbystrek.kairos.api.monitoring.infrastructure.config;

import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class MetricsExportConfiguration {

    @Bean
    MeterFilter boundedHttpRouteMetrics() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri", 200, MeterFilter.deny());
    }

    @Bean
    @Order(0)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain metricsExportSecurityFilterChain(
            HttpSecurity http,
            @Value("${kairos.monitoring.token-location:file:/run/secrets/metrics-token}") Resource tokenLocation
    ) throws Exception {
        var token = readToken(tokenLocation);
        var entryPoint = new BearerTokenAuthenticationEntryPoint();
        AuthenticationManager authenticationManager = authentication -> {
            var supplied = ((BearerTokenAuthenticationToken) authentication).getToken()
                    .getBytes(StandardCharsets.US_ASCII);
            if (token == null || !MessageDigest.isEqual(token, supplied)) {
                throw new BadCredentialsException("Invalid metrics credential");
            }
            return UsernamePasswordAuthenticationToken.authenticated(
                    "metrics", null, AuthorityUtils.createAuthorityList("METRICS_EXPORT"));
        };
        var filter = new BearerTokenAuthenticationFilter(authenticationManager);
        filter.setAuthenticationEntryPoint(entryPoint);

        return http.securityMatcher("/actuator/prometheus", "/actuator/prometheus/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasAuthority("METRICS_EXPORT")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                .build();
    }

    static byte[] readToken(Resource location) {
        try (var input = location.getInputStream()) {
            var bytes = input.readNBytes(131);
            var token = new String(bytes, StandardCharsets.US_ASCII).replaceFirst("\\r?\\n$", "");
            if (!token.matches("[A-Za-z0-9_-]{43,128}")) {
                log.warn("Metrics export disabled: credential file is not 43-128 base64url characters");
                return null;
            }
            return token.getBytes(StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            // An optional export credential must fail closed without disabling the API.
            log.info("Metrics export disabled: no readable credential file");
            return null;
        }
    }
}
