package pl.karolbystrek.kairos.api.order.application;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.application.exception.ExternalOrderConflictException;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreation;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderCreationResult;
import pl.karolbystrek.kairos.api.order.application.model.ExternalOrderPage;
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.util.Hashing;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ExternalOrderService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAXIMUM_PAGE_SIZE = 100;
    public static final int MAXIMUM_IDEMPOTENCY_KEY_BYTES = 255;

    private final DatabaseAccessContext databaseAccess;
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
        databaseAccess.integration(principal);
        if (!principal.scopes().contains(ApiKeyScope.ORDERS_READ)) {
            throw new IntegrationAccessDeniedException("The API Key does not grant the required scope");
        }
        if (requestedLocationId != null) {
            principal.requireLocationAccess(requestedLocationId);
        }
        var cursor = cursorCodec.decode(cursorValue);
        var limit = pageSize(requestedLimit);
        var locationIds = requestedLocationId != null
                ? List.of(requestedLocationId)
                : List.copyOf(principal.locationIds());
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        // One index-ordered query per location: a single IN query would sort the whole history.
        var content = (locationIds.size() > 1 ? locationIds.stream().map(List::of) : Stream.of(locationIds))
                .flatMap(ids -> orderRepository.findBy(
                        // findBy avoids the COUNT query that findAll(spec, Pageable) runs on full pages.
                        orderSpecification(ids, status, cursor),
                        query -> query.sortBy(sort).limit(limit + 1).all()
                ).stream())
                // Ties compare ids as text: UUID.compareTo is signed, PostgreSQL compares bytes as the cursor does.
                .sorted(Comparator.comparing(CustomerOrder::getCreatedAt)
                        .thenComparing((CustomerOrder order) -> order.getId().toString()).reversed())
                .limit(limit + 1)
                .toList();
        var hasNext = content.size() > limit;
        var visibleOrders = hasNext ? content.subList(0, limit) : content;
        var items = visibleOrders.stream()
                .map(OrderView::from)
                .toList();
        var nextCursor = hasNext
                ? cursorCodec.encode(visibleOrders.getLast())
                : null;
        return new ExternalOrderPage(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public OrderView find(ApiKeyPrincipal principal, UUID orderId) {
        databaseAccess.integration(principal);
        if (!principal.scopes().contains(ApiKeyScope.ORDERS_READ)) {
            throw new IntegrationAccessDeniedException("The API Key does not grant the required scope");
        }
        var order = orderRepository.findByIdAndLocationIdIn(orderId, principal.locationIds())
                .orElseThrow(ExternalOrderService::orderNotFound);
        return OrderView.from(order);
    }

    @Transactional
    public ExternalOrderCreationResult create(
            ApiKeyPrincipal principal,
            UUID locationId,
            String label,
            List<OrderItem> items,
            String idempotencyKey
    ) {
        databaseAccess.integrationCommand(principal);
        principal.requireScope(ApiKeyScope.ORDERS_WRITE);
        principal.requireLocationAccess(locationId);
        var validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        var requestFingerprint = creationFingerprint(locationId, label, items);
        var location = locationRepository.findForUpdateById(locationId)
                .filter(candidate -> candidate.getTenantId().equals(principal.tenantId()))
                .filter(candidate -> candidate.isEnabled())
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
            return new ExternalOrderCreationResult(OrderView.from(existing), true);
        }

        var now = clock.instant();
        var order = commandService.create(
                location,
                label,
                items,
                new ExternalOrderCreation(
                        principal.integrationId(),
                        validatedIdempotencyKey,
                        requestFingerprint
                ),
                now
        );
        return new ExternalOrderCreationResult(OrderView.from(order), false);
    }

    @Transactional
    public OrderView updateStatus(
            ApiKeyPrincipal principal,
            UUID orderId,
            OrderStatus target
    ) {
        databaseAccess.integrationCommand(principal);
        principal.requireScope(ApiKeyScope.ORDERS_WRITE);
        // Location before order, as create does, so the outbox FK check cannot deadlock.
        var locationId = orderRepository.findLocationIdById(orderId)
                .orElseThrow(ExternalOrderService::orderNotFound);
        locationRepository.findForShareById(locationId);
        var order = orderRepository.findForUpdateByIdAndLocationIdIn(
                        orderId,
                        principal.locationIds()
                )
                .orElseThrow(ExternalOrderService::orderNotFound);
        if (!principal.tenantId().equals(order.getLocation().getTenantId())) {
            throw orderNotFound();
        }
        commandService.updateStatus(order, target, clock.instant());
        return OrderView.from(order);
    }

    private static Specification<CustomerOrder> orderSpecification(
            List<UUID> locationIds,
            OrderStatus status,
            ExternalOrderCursorCodec.Cursor cursor
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(root.get("location").get("id").in(locationIds));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (cursor != null) {
                var createdAt = root.<Instant>get("createdAt");
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

    // Items are sorted by name so equal selections in a different order replay the same order.
    private static String creationFingerprint(UUID locationId, String label, List<OrderItem> items) {
        var canonicalItems = items.stream()
                .sorted(Comparator.comparing(OrderItem::name))
                .map(item -> item.quantity() + "\t" + item.name())
                .collect(Collectors.joining("\n"));
        var canonicalInput = locationId + "\n" + (label == null ? "NO_LABEL" : "LABEL\n" + label)
                + "\nITEMS\n" + canonicalItems;
        return Hashing.sha256Hex(canonicalInput);
    }

    private static ResourceNotFoundException orderNotFound() {
        return new ResourceNotFoundException("Order was not found");
    }
}
