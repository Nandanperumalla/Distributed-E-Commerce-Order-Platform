package com.demo.orderplatform.payment.service;

import com.demo.orderplatform.payment.domain.PaymentRecord;
import com.demo.orderplatform.payment.domain.PaymentStatus;
import com.demo.orderplatform.payment.store.PaymentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository repository, PaymentGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    /**
     * Returns the payment for an order, charging the card only if this is the
     * first time we have seen it. Kafka delivers at least once, so "have I
     * already charged this?" has to be answered by the database, not by hoping.
     */
    @Transactional
    public PaymentRecord authorize(String orderId, String customerId, long amountCents) {
        UUID id = UUID.fromString(orderId);

        Optional<PaymentRecord> settled = repository.find(id);
        if (settled.isPresent()) {
            log.info("payment for order {} already settled as {}", orderId, settled.get().status());
            return settled.get();
        }

        PaymentGateway.Decision decision = gateway.authorize(customerId, amountCents);
        UUID paymentId = UUID.randomUUID();

        boolean recorded = repository.insertIfAbsent(
                id, paymentId, decision.status(), amountCents, decision.reason());

        if (!recorded) {
            // Another consumer thread got there while the gateway call was in
            // flight. Its answer is the one that counts.
            return repository.find(id).orElseThrow();
        }

        log.info("order {} payment {} -> {}", orderId, paymentId, decision.status());
        return new PaymentRecord(orderId, paymentId.toString(), decision.status(),
                amountCents, decision.reason(), Instant.now());
    }

    public Optional<PaymentRecord> find(UUID orderId) {
        return repository.find(orderId);
    }

    /** Exposed for the listener's branch on the result. */
    public static boolean isAuthorized(PaymentRecord record) {
        return record.status() == PaymentStatus.AUTHORIZED;
    }
}
