package pl.karolbystrek.kairos.api.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import pl.karolbystrek.kairos.api.analytics.application.UsageAnalyticsService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.analytics.infrastructure.web.UsageRateLimiter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UsageAnalyticsControllerTests {
    @Test
    void rejectsInvalidPanelBatchesIncludingNestedFieldsAndUuid() {
        var service = mock(UsageAnalyticsService.class);
        var controller = new UsageAnalyticsController(service, mock(UsageRateLimiter.class), JsonMapper.builder().build());
        var principal = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), TenantRole.ADMIN);
        for (var body : new String[]{"null", "{\"actions\":[]}", "{\"actions\":[null]}",
                "{\"actions\":[{\"event\":\"queue_opened\",\"locationId\":\"invalid\"}]}",
                "{\"actions\":[{\"event\":\"queue_opened\",\"count\":10}]}",
                "{\"actions\":[" + String.join(",", Collections.nCopies(21, "{\"event\":\"queue_opened\"}")) + "]}"}) {
            var request = new MockHttpServletRequest();
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> controller.panel(principal, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                    failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedAndInvalidRequestsBeforeRecording() {
        var service = mock(UsageAnalyticsService.class);
        var controller = new UsageAnalyticsController(service, mock(UsageRateLimiter.class), JsonMapper.builder().build());
        for (var body : new String[]{" ".repeat(8193), "null", "{\"event\":\"unknown\"}",
                "{\"event\":\"review_link_clicked\",\"orderId\":\"extra\"}",
                "{\"event\":\"review_link_clicked\"} {}"}) {
            var request = new MockHttpServletRequest();
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> controller.review(UUID.randomUUID(), request)).isInstanceOfSatisfying(ResponseStatusException.class,
                    failure -> assertThat(failure.getStatusCode()).isEqualTo(body.length() > 8192
                            ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(service);
    }
}
