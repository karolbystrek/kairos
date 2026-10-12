package pl.karolbystrek.kairos.api.analytics.api;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.analytics.application.AnalyticsReportService;
import pl.karolbystrek.kairos.api.analytics.application.AnalyticsReportService.Range;
import pl.karolbystrek.kairos.api.analytics.application.AnalyticsReportService.Report;
import pl.karolbystrek.kairos.api.monitoring.application.PlatformHealthService;
import pl.karolbystrek.kairos.api.monitoring.application.PlatformHealthService.Health;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class AnalyticsReportController {
    private final AnalyticsReportService reports;
    private final PlatformHealthService health;

    @GetMapping("/analytics/v1/report")
    Report restaurant(@AuthenticationPrincipal StaffPrincipal principal,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam String zone, @RequestParam(required = false) UUID locationId) {
        return reports.restaurant(principal, new Range(from, to, zone), locationId);
    }

    @GetMapping("/platform-analytics/v1/report")
    Report platform(@AuthenticationPrincipal StaffPrincipal principal,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam String zone) {
        return reports.platform(principal, new Range(from, to, zone));
    }

    @GetMapping("/platform-analytics/v1/health")
    Health health(@AuthenticationPrincipal StaffPrincipal principal) {
        reports.requirePlatform(principal);
        return health.current();
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    ProblemDetail denied(StaffAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The reporting parameters are invalid");
    }
}
