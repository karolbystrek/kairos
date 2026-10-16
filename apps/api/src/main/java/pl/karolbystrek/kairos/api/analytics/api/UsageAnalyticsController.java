package pl.karolbystrek.kairos.api.analytics.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.analytics.application.UsageAnalyticsService;
import pl.karolbystrek.kairos.api.analytics.application.UsageAnalyticsService.Action;
import pl.karolbystrek.kairos.api.analytics.infrastructure.web.UsageRateLimiter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class UsageAnalyticsController {
    private static final Set<String> PANEL_EVENTS = Set.of("queue_opened", "menu_editor_opened",
            "integration_settings_opened", "order_creation_form_opened");
    private static final Set<String> REVIEW_EVENTS = Set.of("review_link_clicked", "review_prompt_dismissed");
    private final UsageAnalyticsService service;
    private final UsageRateLimiter limiter;
    private final ObjectMapper mapper;

    record PanelRequest(List<Action> actions) {}
    record ReviewRequest(String event) {}

    @PostMapping(path = "/usage-analytics/v1/actions", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void panel(@AuthenticationPrincipal StaffPrincipal principal, HttpServletRequest request) throws IOException {
        limiter.acquire("staff:" + principal.accountId());
        var body = read(request, PanelRequest.class);
        if (body == null || body.actions() == null || body.actions().isEmpty() || body.actions().size() > 20
                || body.actions().stream().anyMatch(action -> action == null || action.event() == null
                    || !PANEL_EVENTS.contains(action.event()))) throw invalid();
        service.panel(principal, body.actions());
    }

    @PostMapping(path = "/tracked-orders/v1/{trackingReference}/review-actions", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void review(@PathVariable UUID trackingReference, HttpServletRequest request) throws IOException {
        var body = read(request, ReviewRequest.class);
        if (body == null || body.event() == null || !REVIEW_EVENTS.contains(body.event())) throw invalid();
        service.review(trackingReference, body.event(), () -> limiter.acquire("tracking:" + trackingReference));
    }

    private <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        var bytes = request.getInputStream().readNBytes(8193);
        if (bytes.length > 8192) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid usage action request");
    }
}
