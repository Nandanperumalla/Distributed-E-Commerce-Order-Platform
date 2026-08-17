package com.demo.orderplatform.common.event;

import java.time.Instant;
import java.util.List;

/**
 * Published after the order row is committed, never before — see
 * {@code OrderEventPublisher} in order-service.
 */
public record OrderCreated(
        String orderId,
        String customerId,
        List<OrderLine> lines,
        long totalCents,
        Instant createdAt) {
}
