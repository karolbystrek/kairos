package pl.karolbystrek.kairos.api.analytics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.analytics.infrastructure.persistence.AnalyticsReportRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsReportService {
    static final int MAXIMUM_DAYS = 90;
    private static final List<String> STATUSES = List.of("IN_PREPARATION", "READY", "COMPLETED", "CANCELED");
    private static final List<String> EVENTS = List.of("queue_opened", "order_creation_form_opened", "menu_editor_opened",
            "integration_settings_opened", "review_link_clicked", "review_prompt_dismissed");
    private final StaffAccessService staff;
    private final DatabaseAccessContext access;
    private final AnalyticsReportRepository reports;
    private final Clock clock;

    public record Range(LocalDate from, LocalDate to, String zone) {}
    public record Mean(long samples, Double meanMs) {}
    public record Day(LocalDate date, Map<String, Long> transitions) {}
    public record LocationTotals(UUID locationId, String name, Map<String, Long> transitions) {}
    public record Report(LocalDate from, LocalDate to, String zone, Instant generatedAt, LocalDate partialDay,
                         Instant firstRecordedHour, Map<String, Long> transitions, Mean preparation, Mean collection,
                         List<Day> days, Map<String, Long> actions, List<LocationTotals> locations) {}

    @Transactional(readOnly = true)
    public Report restaurant(StaffPrincipal principal, Range range, UUID locationId) {
        var context = staff.resolve(principal);
        if (!context.isTenantAdmin()) {
            if (context.assignmentRole() != AssignmentRole.MANAGER) {
                throw new StaffAccessDeniedException("Analytics are available to administrators and managers");
            }
            if (locationId != null && !locationId.equals(context.locationId())) {
                throw new StaffAccessDeniedException("The account cannot access this location");
            }
            return report(context.tenantId(), context.locationId(), range, false);
        }
        if (locationId != null && !reports.tenantLocationExists(context.tenantId(), locationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location was not found");
        }
        return report(context.tenantId(), locationId, range, locationId == null);
    }

    @Transactional(readOnly = true)
    public Report platform(StaffPrincipal principal, Range range) {
        access.platformAnalytics(principal);
        return report(null, null, range, false);
    }

    @Transactional(readOnly = true)
    public void requirePlatform(StaffPrincipal principal) {
        access.platformAnalytics(principal);
    }

    private Report report(UUID tenantId, UUID locationId, Range range, boolean byLocation) {
        var zone = zone(range.zone());
        var today = LocalDate.now(clock.withZone(zone));
        if (range.from() == null || range.to() == null || range.from().isAfter(range.to()) || range.to().isAfter(today)
                || ChronoUnit.DAYS.between(range.from(), range.to()) >= MAXIMUM_DAYS) {
            throw invalid("The reporting range must be at most 90 past calendar days");
        }
        var from = range.from().atStartOfDay(zone).toInstant();
        var to = range.to().plusDays(1).atStartOfDay(zone).toInstant();

        var days = new LinkedHashMap<LocalDate, Map<String, Long>>();
        for (var day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) days.put(day, zeros(STATUSES));
        var totals = zeros(STATUSES);
        long preparationSamples = 0, preparationMs = 0, collectionSamples = 0, collectionMs = 0;
        for (var row : reports.orders(tenantId, locationId, from, to, zone.getId())) {
            var counts = days.get(row.day());
            if (counts != null) counts.merge(row.status(), row.transitions(), Long::sum);
            totals.merge(row.status(), row.transitions(), Long::sum);
            if (row.status().equals("READY")) {
                preparationSamples += row.samples();
                preparationMs += row.durationMs();
            } else if (row.status().equals("COMPLETED")) {
                collectionSamples += row.samples();
                collectionMs += row.durationMs();
            }
        }

        var actions = zeros(tenantId == null ? EVENTS : List.of("review_link_clicked", "review_prompt_dismissed"));
        reports.usage(tenantId, locationId, from, to).forEach(row -> {
            if (actions.containsKey(row.event())) actions.put(row.event(), row.actions());
        });

        List<LocationTotals> locations = null;
        if (byLocation) {
            var byId = new LinkedHashMap<UUID, LocationTotals>();
            for (var row : reports.locations(tenantId, from, to)) {
                byId.computeIfAbsent(row.locationId(), id -> new LocationTotals(id, row.name(), zeros(STATUSES)))
                        .transitions().merge(row.status(), row.transitions(), Long::sum);
            }
            locations = byId.values().stream().sorted(Comparator.comparing(LocationTotals::name,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(LocationTotals::locationId)).toList();
        }

        return new Report(range.from(), range.to(), zone.getId(), clock.instant(),
                range.to().equals(today) ? today : null, reports.firstRecordedHour(tenantId, locationId), totals,
                mean(preparationSamples, preparationMs), mean(collectionSamples, collectionMs),
                days.entrySet().stream().map(day -> new Day(day.getKey(), day.getValue())).toList(), actions, locations);
    }

    // Means merge duration totals and sample counts; no samples means no mean rather than zero.
    static Mean mean(long samples, long totalMs) {
        return new Mean(samples, samples == 0 ? null : (double) totalMs / samples);
    }

    static ZoneId zone(String zone) {
        if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) {
            throw invalid("The reporting time zone must be an IANA time zone");
        }
        return ZoneId.of(zone);
    }

    private static Map<String, Long> zeros(List<String> keys) {
        var values = new LinkedHashMap<String, Long>();
        keys.forEach(key -> values.put(key, 0L));
        return values;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
