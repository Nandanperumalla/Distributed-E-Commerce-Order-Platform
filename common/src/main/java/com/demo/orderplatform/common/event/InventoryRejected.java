package com.demo.orderplatform.common.event;

/** Not enough stock for at least one line; nothing was decremented. */
public record InventoryRejected(String orderId, String sku, String reason) {
}
