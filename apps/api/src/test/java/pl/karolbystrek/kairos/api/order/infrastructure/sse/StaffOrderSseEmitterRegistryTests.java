package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffOrderSseEmitterRegistryTests {
    private final OrderService orders = mock(OrderService.class);
    private final SseEmitter emitter = mock(SseEmitter.class);
    private final StaffOrderSseEmitterRegistry registry = new StaffOrderSseEmitterRegistry(orders, ignored -> emitter);
    private final StaffPrincipal principal = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), TenantRole.ADMIN);

    @Test
    void initialSnapshotAndCommittedChangesUseTheSameAuthorizedQueueAndScope() throws Exception {
        var location = UUID.randomUUID();
        when(orders.listOrders(principal, location, null)).thenReturn(List.of());
        registry.subscribe(principal, location);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(orders, emitter);

        registry.forward(event(UUID.randomUUID(), location));
        verify(orders, never()).listOrders(principal, location, null);
        registry.forward(event(principal.tenantId(), location));
        verify(orders).listOrders(principal, location, null);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void rechecksTenantWideAccessAndClosesTheConnectionWhenItIsDenied() {
        when(orders.listOrders(principal, null, null)).thenReturn(List.of());
        registry.subscribe(principal, null);
        when(orders.listOrders(principal, null, null)).thenThrow(new IllegalStateException("access revoked"));

        registry.refresh();

        verify(emitter).complete();
        clearInvocations(orders);
        registry.refresh();
        verify(orders, never()).listOrders(principal, null, null);
    }

    @Test
    void deniesTheInitialRequestBeforeOpeningAnEmitter() {
        when(orders.listOrders(principal, null, null)).thenThrow(new IllegalStateException("access denied"));
        assertThatThrownBy(() -> registry.subscribe(principal, null)).isInstanceOf(IllegalStateException.class);
    }

    private OrderStatusChangedEvent event(UUID tenantId, UUID locationId) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.READY,
                Instant.now(), "42", null, tenantId, locationId);
    }
}
