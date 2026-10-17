package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.application.StaffAuthenticationService;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffOrderSseEmitterRegistryTests {
    private final OrderService orders = mock(OrderService.class);
    private final SseEmitter emitter = mock(SseEmitter.class);
    private final StaffAuthenticationService authentication = mock(StaffAuthenticationService.class);
    private final StaffOrderSseEmitterRegistry registry =
            new StaffOrderSseEmitterRegistry(orders, authentication, ignored -> emitter);
    private final Instant signedInAt = Instant.parse("2026-07-24T12:00:00Z");
    private final StaffPrincipal principal = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), TenantRole.ADMIN);

    @Test
    void initialSnapshotAndCommittedChangesUseTheSameAuthorizedQueueAndScope() throws Exception {
        var location = UUID.randomUUID();
        when(orders.listOrders(principal, location, null)).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, location);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(orders, times(1)).listOrders(principal, location, null);
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
        registry.subscribe(principal, "staff", signedInAt, null);
        when(orders.listOrders(principal, null, null)).thenThrow(new IllegalStateException("access revoked"));

        registry.refresh();

        verify(emitter).complete();
        clearInvocations(orders);
        registry.refresh();
        verify(orders, never()).listOrders(principal, null, null);
    }

    @Test
    void closesTheStreamWhenItsSessionWasRevoked() {
        when(orders.listOrders(principal, null, null)).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, null);
        verify(authentication).authenticate("staff", signedInAt);
        doThrow(new StaffAccessDeniedException("Session was revoked")).when(authentication)
                .authenticate("staff", signedInAt);

        registry.revalidate();

        verify(emitter).complete();
    }

    @Test
    void closesTheStreamWhenItsLocationAccessWasRevoked() {
        var location = UUID.randomUUID();
        when(orders.listOrders(principal, location, null)).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, location);
        doThrow(new StaffAccessDeniedException("Assignment removed")).when(orders).requireQueueAccess(principal, location);

        registry.revalidate();

        verify(emitter).complete();
        assertThat(registry.groupCount()).isZero();
    }

    @Test
    void devicesWatchingOneLocationShareOneQueryPerChange() throws Exception {
        var location = UUID.randomUUID();
        var colleague = new StaffPrincipal(UUID.randomUUID(), principal.tenantId(), TenantRole.MEMBER);
        var first = mock(SseEmitter.class);
        var second = mock(SseEmitter.class);
        var emitters = new java.util.ArrayDeque<>(List.of(first, second));
        var shared = new StaffOrderSseEmitterRegistry(orders, authentication, ignored -> emitters.removeFirst());
        when(orders.listOrders(any(), any(), any())).thenReturn(List.of());
        shared.subscribe(principal, "owner", signedInAt, location);
        shared.subscribe(colleague, "staff", signedInAt, location);
        clearInvocations(orders, first, second);

        shared.forward(event(principal.tenantId(), location));

        verify(orders, times(1)).listOrders(any(), any(), any());
        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(second).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(shared.groupCount()).isOne();
    }

    @Test
    void deniesTheInitialRequestBeforeOpeningAnEmitter() {
        when(orders.listOrders(principal, null, null)).thenThrow(new IllegalStateException("access denied"));
        assertThatThrownBy(() -> registry.subscribe(principal, "staff", signedInAt, null)).isInstanceOf(IllegalStateException.class);
    }

    private OrderStatusChangedEvent event(UUID tenantId, UUID locationId) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.READY,
                Instant.now(), 42L, "42", null, tenantId, locationId);
    }
}
