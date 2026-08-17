package com.demo.orderplatform.order.api;

public record PlaceOrderResponse(String orderId, String status, boolean replayed) {
}
