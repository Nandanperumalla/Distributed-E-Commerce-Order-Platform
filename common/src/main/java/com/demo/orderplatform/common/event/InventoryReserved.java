package com.demo.orderplatform.common.event;

import java.util.List;

/**
 * Stock is decremented and held for this order. Carries the total so
 * payment-service never has to call back into order-service to price it.
 */
public record InventoryReserved(
        String orderId,
        String customerId,
        List<OrderLine> lines,
        long totalCents) {
}
