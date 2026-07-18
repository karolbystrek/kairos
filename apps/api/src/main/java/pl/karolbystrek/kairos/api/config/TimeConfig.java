package pl.karolbystrek.kairos.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
