package pl.karolbystrek.kairos.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
			.build();
	}
}
