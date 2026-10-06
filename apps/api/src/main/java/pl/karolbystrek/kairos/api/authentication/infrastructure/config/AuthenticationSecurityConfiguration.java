package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class AuthenticationSecurityConfiguration {
    public static final int INACTIVITY_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    HttpSessionIdResolver httpSessionIdResolver() {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("__Host-session");
        cookie.setCookiePath("/");
        cookie.setUseSecureCookie(true);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        cookie.setCookieMaxAge(INACTIVITY_SECONDS);
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(cookie);
        return resolver;
    }
}
