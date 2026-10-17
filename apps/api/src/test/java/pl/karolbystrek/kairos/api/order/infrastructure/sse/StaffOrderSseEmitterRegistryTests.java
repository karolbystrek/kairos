package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.application.StaffAuthenticationService;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.api.model.OrderResponse;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.assertj.core.api.Assertions.assertThat;
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
        when(orders.listOrdersForRefresh(eq(principal), eq(location), anyMap())).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, location);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        // One synchronous access check, then the refresh worker's snapshot.
        verify(orders, times(2)).listOrdersForRefresh(eq(principal), eq(location), anyMap());
        clearInvocations(orders, emitter);

        registry.forward(event(UUID.randomUUID(), location));
        verify(orders, never()).listOrdersForRefresh(eq(principal), eq(location), anyMap());
        registry.forward(event(principal.tenantId(), location));
        verify(orders).listOrdersForRefresh(eq(principal), eq(location), anyMap());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void rechecksTenantWideAccessAndClosesTheConnectionWhenItIsDenied() {
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, null);
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenThrow(new IllegalStateException("access revoked"));

        registry.refresh();

        verify(emitter).complete();
        clearInvocations(orders);
        registry.refresh();
        verify(orders, never()).listOrdersForRefresh(eq(principal), isNull(), anyMap());
    }

    @Test
    void closesTheStreamWhenItsSessionWasRevoked() {
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, null);
        verify(authentication).authenticate("staff", signedInAt);
        doThrow(new StaffAccessDeniedException("Session was revoked")).when(authentication)
                .authenticate("staff", signedInAt);

        registry.refresh();

        verify(emitter).complete();
    }

    @Test
    void authenticatesEachSessionOncePerCycle() {
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenReturn(List.of());
        registry.subscribe(principal, "staff", signedInAt, null);
        registry.subscribe(principal, "staff", signedInAt, null);
        clearInvocations(authentication);

        registry.refresh();

        verify(authentication, times(1)).authenticate("staff", signedInAt);
    }

    @Test
    void deniesTheInitialRequestBeforeOpeningAnEmitter() {
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenThrow(new IllegalStateException("access denied"));
        assertThatThrownBy(() -> registry.subscribe(principal, "staff", signedInAt, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void coalescesBurstRefreshesAndSharesOneCycleMapAcrossSessions() {
        var tasks = new ArrayDeque<Runnable>();
        var first = mock(SseEmitter.class);
        var second = mock(SseEmitter.class);
        var emitters = new ArrayDeque<>(List.of(first, second));
        var queued = new StaffOrderSseEmitterRegistry(orders, authentication, ignored -> emitters.removeFirst(), tasks::add);
        var other = new StaffPrincipal(UUID.randomUUID(), principal.tenantId(), TenantRole.ADMIN);
        when(orders.listOrdersForRefresh(any(), isNull(), anyMap())).thenReturn(List.of());
        queued.subscribe(principal, "first", signedInAt, null);
        queued.subscribe(other, "second", signedInAt, null);
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        clearInvocations(orders, authentication);
        var maps = new ArrayList<Map<?, ?>>();
        when(orders.listOrdersForRefresh(any(), isNull(), anyMap())).thenAnswer(invocation -> {
            maps.add(invocation.getArgument(2));
            return List.of();
        });

        for (var index = 0; index < 100; index++) queued.refresh();
        assertThat(tasks).hasSize(1);
        while (!tasks.isEmpty()) tasks.removeFirst().run();

        assertThat(maps).hasSize(2);
        assertThat(maps.get(0)).isSameAs(maps.get(1));
        verify(authentication).authenticate("first", signedInAt);
        verify(authentication).authenticate("second", signedInAt);
        queued.refresh();
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertThat(maps.get(2)).isNotSameAs(maps.getFirst());
    }

    @Test
    void aChangeDuringAReadRunsAnotherCycleAndCannotDeliverTheOlderQueueLast() {
        var tasks = new ArrayDeque<Runnable>();
        var labels = new ArrayList<String>();
        var recording = new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) {
                for (var part : builder.build()) {
                    if (part.getData() instanceof List<?> queue && !queue.isEmpty()) {
                        labels.add(((OrderResponse) queue.getFirst()).label());
                    }
                }
            }
        };
        var queued = new StaffOrderSseEmitterRegistry(orders, authentication, ignored -> recording, tasks::add);
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenReturn(List.of());
        queued.subscribe(principal, "staff", signedInAt, null);
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        var reads = new int[1];
        when(orders.listOrdersForRefresh(eq(principal), isNull(), anyMap())).thenAnswer(invocation -> {
            var label = ++reads[0] == 1 ? "old" : "new";
            if (reads[0] == 1) queued.refresh();
            return List.of(new OrderView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    42L, label, List.of(), OrderStatus.READY, signedInAt, signedInAt));
        });

        queued.refresh();
        while (!tasks.isEmpty()) tasks.removeFirst().run();

        assertThat(reads[0]).isEqualTo(2);
        assertThat(labels).containsExactly("new");
    }

    private OrderStatusChangedEvent event(UUID tenantId, UUID locationId) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.READY,
                Instant.now(), 42L, "42", null, tenantId, locationId);
    }
}
