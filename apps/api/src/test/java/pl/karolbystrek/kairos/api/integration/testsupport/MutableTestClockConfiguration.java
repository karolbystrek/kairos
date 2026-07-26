package pl.karolbystrek.kairos.api.integration.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MutableTestClockConfiguration {

    @Bean
    @Primary
    MutableTestClock mutableTestClock() {
        return new MutableTestClock();
    }
}
