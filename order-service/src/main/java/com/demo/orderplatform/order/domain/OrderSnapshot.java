package com.demo.orderplatform.order.domain;

import com.demo.orderplatform.common.event.OrderLine;
import java.time.Instant;
import java.util.List;

public record OrderSnapshot(
        String orderId,
        String customerId,
        OrderStatus status,
        long totalCents,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderLine> lines) {
}
