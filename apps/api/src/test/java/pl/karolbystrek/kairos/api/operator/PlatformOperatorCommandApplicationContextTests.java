package pl.karolbystrek.kairos.api.operator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.karolbystrek.kairos.api.account.application.PlatformOperatorAccountService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "kairos.scheduling.enabled=false"
)
class PlatformOperatorCommandApplicationContextTests {

    @Autowired
    private PlatformOperatorAccountService accountService;

    @Test
    void startsTheNonWebOperationalContextWithTheAccountService() {
        assertThat(accountService).isNotNull();
    }
}
