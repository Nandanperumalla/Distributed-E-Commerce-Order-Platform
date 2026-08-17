package com.demo.orderplatform.order.service;

import com.demo.orderplatform.common.event.OrderCreated;
import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.common.event.Topics;
import com.demo.orderplatform.order.messaging.OutboundEvent;
import com.demo.orderplatform.order.store.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean so the transaction proxy actually applies — a {@code @Transactional}
 * method called from a sibling method on the same object goes straight through.
 */
@Component
public class OrderWriter {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    public OrderWriter(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public void create(UUID orderId, String customerId, List<OrderLine> lines, long totalCents) {
        repository.insert(orderId, customerId, totalCents, lines);
        // Handed to Kafka after commit, not now. See OrderEventPublisher.
        events.publishEvent(new OutboundEvent(Topics.ORDER_CREATED, orderId.toString(),
                new OrderCreated(orderId.toString(), customerId, lines, totalCents, Instant.now())));
    }
}
