package com.demo.orderplatform.common.event;

public record PaymentAuthorized(String orderId, String paymentId, long amountCents) {
}
