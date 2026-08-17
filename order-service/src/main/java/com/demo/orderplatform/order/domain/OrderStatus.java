package com.demo.orderplatform.order.domain;

/**
 * <pre>
 *   PENDING ──inventory.reserved──> INVENTORY_RESERVED ──payment.authorized──> CONFIRMED
 *      │                                    │
 *      └──inventory.rejected──> REJECTED    └──payment.declined──> CANCELLED (stock released)
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
