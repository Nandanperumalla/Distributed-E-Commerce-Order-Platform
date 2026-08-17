package com.demo.orderplatform.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.orderplatform.payment.domain.PaymentStatus;
import com.demo.orderplatform.payment.service.PaymentGateway;
import org.junit.jupiter.api.Test;

/** Fast, no containers — just the decision rules the demo relies on. */
class PaymentGatewayTest {

    private final PaymentGateway gateway = new PaymentGateway(0L, 500_000L);

    @Test
    void ordinaryOrdersAreAuthorized() {
        PaymentGateway.Decision decision = gateway.decide("cust-1", 129_900L);

        assertThat(decision.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void amountsOverTheLimitAreDeclined() {
        PaymentGateway.Decision decision = gateway.decide("cust-1", 500_001L);

        assertThat(decision.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(decision.reason()).contains("exceeds authorization limit");
    }

    @Test
    void theLimitItselfIsStillAuthorized() {
        assertThat(gateway.decide("cust-1", 500_000L).status()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void theDemoDeclineCustomerIsAlwaysDeclined() {
        PaymentGateway.Decision decision = gateway.decide("cust-broke-decline", 100L);

        assertThat(decision.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(decision.reason()).isEqualTo("card declined by issuer");
    }
}
