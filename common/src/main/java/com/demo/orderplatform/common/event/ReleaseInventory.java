package com.demo.orderplatform.common.event;

/** Compensating command: payment failed, put the stock back. */
public record ReleaseInventory(String orderId, String reason) {
}
