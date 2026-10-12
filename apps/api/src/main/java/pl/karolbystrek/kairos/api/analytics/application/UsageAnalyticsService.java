package pl.karolbystrek.kairos.api.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.UsageAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsageAnalyticsService {
    private final StaffAccessService staff;
    private final DatabaseAccessContext access;
    private final CustomerOrderRepository orders;
    private final UsageAnalyticsRepository database;
    private final Clock clock;

    public record Action(String event, UUID locationId) {}

    @Transactional
    public void panel(StaffPrincipal principal, List<Action> actions) {
        var context = staff.resolve(principal);
        var now = Timestamp.from(clock.instant());
        for (var action : actions.stream().sorted(Comparator.comparing(Action::event)
                .thenComparing(Action::locationId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
            if (!database.record(context.tenantId(), action.locationId(), action.event(), now)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action scope is not accessible");
            }
        }
    }

    @Transactional
    public void review(UUID reference, String event) {
        access.trackedOrders(List.of(reference));
        var order = orders.findByTrackingReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked order was not found"));
        var now = clock.instant();
        if (!order.isReviewEligible() || order.getReviewDueAt().isAfter(now)) {
            throw new ResourceNotFoundException("Review invitation was not found");
        }
        if (!database.record(order.getLocation().getTenantId(), order.getLocation().getId(), event, Timestamp.from(now))) {
            throw new ResourceNotFoundException("Review invitation was not found");
        }
    }
}
