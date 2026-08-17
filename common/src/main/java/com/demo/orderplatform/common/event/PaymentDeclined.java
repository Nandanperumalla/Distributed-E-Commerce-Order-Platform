package com.demo.orderplatform.common.event;

public record PaymentDeclined(String orderId, String paymentId, long amountCents, String reason) {
}
