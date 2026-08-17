package com.demo.orderplatform.payment.domain;

import java.time.Instant;

public record PaymentRecord(
        String orderId,
        String paymentId,
        PaymentStatus status,
        long amountCents,
        String reason,
        Instant createdAt) {
}
