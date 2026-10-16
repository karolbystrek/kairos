package pl.karolbystrek.kairos.api.order.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.karolbystrek.kairos.api.order.domain.OrderStatus.CANCELED;
import static pl.karolbystrek.kairos.api.order.domain.OrderStatus.COMPLETED;
import static pl.karolbystrek.kairos.api.order.domain.OrderStatus.EXPIRED;
import static pl.karolbystrek.kairos.api.order.domain.OrderStatus.IN_PREPARATION;
import static pl.karolbystrek.kairos.api.order.domain.OrderStatus.READY;

class OrderStatusTests {

    // Written out by hand so a changed rule shows up as a failing pair, not as a restated implementation.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            IN_PREPARATION, Set.of(READY, CANCELED),
            READY, Set.of(COMPLETED, CANCELED),
            COMPLETED, Set.of(),
            CANCELED, Set.of(),
            EXPIRED, Set.of());

    static Stream<Arguments> everyPair() {
        return Stream.of(OrderStatus.values()).flatMap(from -> Stream.of(OrderStatus.values())
                .map(to -> Arguments.of(from, to, ALLOWED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("everyPair")
    void transitionsMatchTheOrderLifecycle(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
