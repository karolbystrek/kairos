package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushEnrollmentRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderOutboxEventRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CustomerPushOutboxFanoutService {

    private final OrderOutboxEventRepository outboxRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerPushEnrollmentRepository enrollmentRepository;
    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerPushPayloadFactory payloadFactory;
    private final CustomerNotificationProperties properties;
    private final Clock clock;

    @Transactional
    public int fanOutAvailable() {
        var events = outboxRepository.findAvailableForPushFanout(
                properties.worker().batchSize()
        );
        for (var event : events) {
            fanOut(event);
        }
        return events.size();
    }

    private void fanOut(OrderOutboxEvent event) {
        var now = clock.instant();
        if (event.getEventType() == OrderEventType.ORDER_CREATED) {
            event.completePushFanout(now);
            return;
        }
        var currentOrder = orderRepository.findById(event.getOrderId()).orElse(null);
        if (currentOrder == null || currentOrder.getStatus() != event.getStatus()) {
            event.completePushFanout(now);
            return;
        }
        var payload = payloadFactory.create(event);
        var deadline = event.getOccurredAt().plus(properties.delivery().freshnessWindow());
        var enrollments = enrollmentRepository.findAllByOrderId(event.getOrderId());
        for (var enrollment : enrollments) {
            var subscription = subscriptionRepository.findById(
                    enrollment.getSubscriptionId()
            ).orElse(null);
            if (subscription == null
                    || deliveryRepository.existsByOutboxEventIdAndSubscriptionId(
                    event.getId(),
                    enrollment.getSubscriptionId()
            )) {
                continue;
            }
            deliveryRepository
                    .findAllBySubscriptionIdAndOrderIdAndStatus(
                            enrollment.getSubscriptionId(),
                            event.getOrderId(),
                            CustomerPushDeliveryStatus.PENDING
                    )
                    .forEach(delivery -> delivery.supersede(now));
            deliveryRepository.save(CustomerPushDelivery.create(
                    event.getId(),
                    enrollment.getSubscriptionId(),
                    event.getOrderId(),
                    subscription.getEndpointHash(),
                    subscription.getEndpointOrigin(),
                    payload,
                    deadline,
                    now
            ));
        }
        if (event.getStatus().isTerminal()) {
            enrollmentRepository.deleteAllByOrderId(event.getOrderId());
        }
        event.completePushFanout(now);
    }
}
