package com.demo.orderplatform.payment.service;

import com.demo.orderplatform.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real card processor. Two things about it are deliberate:
 *
 * <ul>
 *   <li>it is slow, configurably so, because the point of putting payments
 *       behind Kafka is that a slow gateway must not slow down order intake;
 *   <li>it declines on rules a demo can trigger on purpose — an amount over the
 *       limit, or a customer id ending in {@code -decline}.
 * </ul>
 */
@Component
public class PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);

    public record Decision(PaymentStatus status, String reason) {
    }

    private final long latencyMs;
    private final long declineAboveCents;

    public PaymentGateway(@Value("${app.gateway.latency-ms}") long latencyMs,
                          @Value("${app.gateway.decline-above-cents}") long declineAboveCents) {
        this.latencyMs = latencyMs;
        this.declineAboveCents = declineAboveCents;
    }

    public Decision authorize(String customerId, long amountCents) {
        sleep();
        return decide(customerId, amountCents);
    }

    /** Pure, so the rules can be tested without waiting for the fake latency. */
    public Decision decide(String customerId, long amountCents) {
        if (customerId != null && customerId.endsWith("-decline")) {
            return new Decision(PaymentStatus.DECLINED, "card declined by issuer");
        }
        if (amountCents > declineAboveCents) {
            return new Decision(PaymentStatus.DECLINED,
                    "amount " + amountCents + " exceeds authorization limit " + declineAboveCents);
        }
        return new Decision(PaymentStatus.AUTHORIZED, null);
    }

    private void sleep() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("gateway call interrupted");
        }
    }
}
