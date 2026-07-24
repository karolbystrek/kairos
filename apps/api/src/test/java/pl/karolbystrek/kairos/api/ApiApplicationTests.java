package pl.karolbystrek.kairos.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

@SpringBootTest
class ApiApplicationTests extends RedisListenerIsolatedIntegrationTest {

    @Test
    void contextLoads() {
    }

}
