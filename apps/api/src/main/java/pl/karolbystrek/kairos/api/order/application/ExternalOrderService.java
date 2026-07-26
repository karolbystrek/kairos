package pl.karolbystrek.kairos.api.order.application;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.application.exception.ExternalOrderConflictException;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreation;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreationResult;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderPage;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderView;
import pl.karolbystrek.kairos.api.order.application.model.OrderInitiator;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalOrderService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAXIMUM_PAGE_SIZE = 100;
    public static final int MAXIMUM_IDEMPOTENCY_KEY_BYTES = 255;

    private final CustomerOrderRepository orderRepository;
    private final LocationRepository locationRepository;
    private final OrderCommandService commandService;
    private final ExternalOrderCursorCodec cursorCodec;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ExternalOrderPage list(
            ApiKeyPrincipal principal,
            UUID requestedLocationId,
            OrderStatus status,
            String cursorValue,
            Integer requestedLimit
    ) {
        principal.requireScope(ApiKeyScope.ORDERS_READ);
        if (requestedLocationId != null) {
            principal.requireLocationAccess(requestedLocationId);
        }
        var cursor = cursorCodec.decode(cursorValue);
        var limit = pageSize(requestedLimit);
        var specification = orderSpecification(
                principal,
                requestedLocationId,
                status,
                cursor
        );
        var page = orderRepository.findAll(
                specification,
                PageRequest.of(
                        0,
                        limit + 1,
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("id")
                        )
                )
        );
        var content = page.getContent();
        var hasNext = content.size() > limit;
        var visibleOrders = hasNext ? content.subList(0, limit) : content;
        var items = visibleOrders.stream()
                .map(ExternalOrderView::from)
                .toList();
        var nextCursor = hasNext
                ? cursorCodec.encode(visibleOrders.getLast())
                : null;
        return new ExternalOrderPage(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public ExternalOrderView find(ApiKeyPrincipal principal, UUID orderId) {
        principal.requireScope(ApiKeyScope.ORDERS_READ);
        var order = orderRepository.findByIdAndLocationIdIn(orderId, principal.locationIds())
                .orElseThrow(ExternalOrderService::orderNotFound);
        return ExternalOrderView.from(order);
    }

    @Transactional
    public ExternalOrderCreationResult create(
            ApiKeyPrincipal principal,
            UUID locationId,
            String customLabel,
            String idempotencyKey
    ) {
        principal.requireScope(ApiKeyScope.ORDERS_WRITE);
        principal.requireLocationAccess(locationId);
        var validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        var requestFingerprint = creationFingerprint(locationId, customLabel);
        var location = locationRepository.findForUpdateById(locationId)
                .filter(candidate -> candidate.getTenantId().equals(principal.tenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Location was not found"));

        var replay = orderRepository
                .findByExternalIntegrationIdAndLocationIdAndExternalIdempotencyKey(
                        principal.integrationId(),
                        locationId,
                        validatedIdempotencyKey
                );
        if (replay.isPresent()) {
            var existing = replay.orElseThrow();
            if (!requestFingerprint.equals(existing.getExternalRequestFingerprint())) {
                throw new ExternalOrderConflictException(
                        "Idempotency-Key was already used with different order input"
                );
            }
            return new ExternalOrderCreationResult(ExternalOrderView.from(existing), true);
        }

        var now = clock.instant();
        var order = commandService.create(
                location,
                customLabel,
                integrationInitiator(principal),
                new ExternalOrderCreation(
                        principal.integrationId(),
                        validatedIdempotencyKey,
                        requestFingerprint
                ),
                now
        );
        return new ExternalOrderCreationResult(ExternalOrderView.from(order), false);
    }

    @Transactional
    public ExternalOrderView updateStatus(
            ApiKeyPrincipal principal,
            UUID orderId,
            OrderStatus target
    ) {
        principal.requireScope(ApiKeyScope.ORDERS_WRITE);
        var order = orderRepository.findForUpdateByIdAndLocationIdIn(
                        orderId,
                        principal.locationIds()
                )
                .orElseThrow(ExternalOrderService::orderNotFound);
        if (!principal.tenantId().equals(order.getLocation().getTenantId())) {
            throw orderNotFound();
        }
        commandService.updateStatus(
                order,
                target,
                integrationInitiator(principal),
                clock.instant()
        );
        return ExternalOrderView.from(order);
    }

    private static Specification<CustomerOrder> orderSpecification(
            ApiKeyPrincipal principal,
            UUID requestedLocationId,
            OrderStatus status,
            ExternalOrderCursorCodec.Cursor cursor
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(root.get("location").get("id").in(principal.locationIds()));
            if (requestedLocationId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("location").get("id"),
                        requestedLocationId
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (cursor != null) {
                var createdAt = root.<java.time.Instant>get("createdAt");
                var id = root.<UUID>get("id");
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.lessThan(createdAt, cursor.createdAt()),
                        criteriaBuilder.and(
                                criteriaBuilder.equal(createdAt, cursor.createdAt()),
                                criteriaBuilder.lessThan(id, cursor.orderId())
                        )
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static int pageSize(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requestedLimit < 1 || requestedLimit > MAXIMUM_PAGE_SIZE) {
            throw new InvalidOrderRequestException(
                    "Order page limit must be between 1 and " + MAXIMUM_PAGE_SIZE
            );
        }
        return requestedLimit;
    }

    private static String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isEmpty()
                || idempotencyKey.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_IDEMPOTENCY_KEY_BYTES) {
            throw new InvalidOrderRequestException(
                    "Idempotency-Key must contain between 1 and 255 UTF-8 bytes"
            );
        }
        return idempotencyKey;
    }

    private static String creationFingerprint(UUID locationId, String customLabel) {
        var canonicalInput = customLabel == null
                ? locationId + "\nAUTO"
                : locationId + "\nCUSTOM\n" + customLabel;
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static OrderInitiator integrationInitiator(ApiKeyPrincipal principal) {
        return OrderInitiator.integration(
                principal.integrationId(),
                principal.apiKeyId(),
                principal.apiKeyVersionId()
        );
    }

    private static ResourceNotFoundException orderNotFound() {
        return new ResourceNotFoundException("Order was not found");
    }
}
