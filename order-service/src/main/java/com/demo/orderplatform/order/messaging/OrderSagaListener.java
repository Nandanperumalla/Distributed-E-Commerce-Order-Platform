package com.demo.orderplatform.order.messaging;

import com.demo.orderplatform.common.event.InventoryRejected;
import com.demo.orderplatform.common.event.InventoryReserved;
import com.demo.orderplatform.common.event.PaymentAuthorized;
import com.demo.orderplatform.common.event.PaymentDeclined;
import com.demo.orderplatform.common.event.ReleaseInventory;
import com.demo.orderplatform.common.event.Topics;
import com.demo.orderplatform.order.domain.OrderStatus;
import com.demo.orderplatform.order.store.OrderRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * order-service owns the saga. Inventory and payments each do one job and
 * announce the result; this class is the only place that decides what an order
 * becomes, and the only place that issues compensation.
 */
@Component
public class OrderSagaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaListener.class);

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    public OrderSagaListener(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, containerFactory = "inventoryReservedFactory")
    @Transactional
    public void onInventoryReserved(InventoryReserved event) {
        UUID orderId = UUID.fromString(event.orderId());
        if (repository.transition(orderId, OrderStatus.INVENTORY_RESERVED, null, OrderStatus.PENDING)) {
            log.info("order {} reserved stock", orderId);
        } else {
            log.debug("ignoring inventory.reserved for {} — already past PENDING", orderId);
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_REJECTED, containerFactory = "inventoryRejectedFactory")
    @Transactional
    public void onInventoryRejected(InventoryRejected event) {
        UUID orderId = UUID.fromString(event.orderId());
        String reason = "insufficient stock for " + event.sku() + ": " + event.reason();
        if (repository.transition(orderId, OrderStatus.REJECTED, reason, OrderStatus.PENDING)) {
            log.info("order {} rejected: {}", orderId, reason);
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_AUTHORIZED, containerFactory = "paymentAuthorizedFactory")
    @Transactional
    public void onPaymentAuthorized(PaymentAuthorized event) {
        UUID orderId = UUID.fromString(event.orderId());
        if (repository.transition(orderId, OrderStatus.CONFIRMED, null, OrderStatus.INVENTORY_RESERVED)) {
            log.info("order {} confirmed, payment {}", orderId, event.paymentId());
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_DECLINED, containerFactory = "paymentDeclinedFactory")
    @Transactional
    public void onPaymentDeclined(PaymentDeclined event) {
        UUID orderId = UUID.fromString(event.orderId());
        if (repository.transition(orderId, OrderStatus.CANCELLED, event.reason(), OrderStatus.INVENTORY_RESERVED)) {
            log.info("order {} cancelled, releasing stock: {}", orderId, event.reason());
            // Guarded by the transition above, so a redelivered decline cannot
            // release the same reservation twice.
            events.publishEvent(new OutboundEvent(Topics.INVENTORY_RELEASE, event.orderId(),
                    new ReleaseInventory(event.orderId(), event.reason())));
        }
    }
}
