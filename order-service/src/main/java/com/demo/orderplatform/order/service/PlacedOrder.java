package com.demo.orderplatform.order.service;

/** @param replayed true when an idempotency key matched an existing order */
public record PlacedOrder(String orderId, boolean replayed) {
}
