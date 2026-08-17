package com.demo.orderplatform.payment.messaging;

import com.demo.orderplatform.common.event.InventoryReserved;
import com.demo.orderplatform.common.event.PaymentAuthorized;
import com.demo.orderplatform.common.event.PaymentDeclined;
import com.demo.orderplatform.common.event.Topics;
import com.demo.orderplatform.payment.domain.PaymentRecord;
import com.demo.orderplatform.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentListener.class);

    private final PaymentService payments;
    private final KafkaTemplate<String, Object> kafka;

    public PaymentListener(PaymentService payments, KafkaTemplate<String, Object> kafka) {
        this.payments = payments;
        this.kafka = kafka;
    }

    /**
     * Payment starts only once stock is actually held. Charging first and then
     * discovering the warehouse is empty means issuing refunds; this way the
     * only compensation we ever need is putting stock back.
     */
    @KafkaListener(topics = Topics.INVENTORY_RESERVED, containerFactory = "inventoryReservedFactory")
    public void onInventoryReserved(InventoryReserved event) {
        PaymentRecord payment = payments.authorize(
                event.orderId(), event.customerId(), event.totalCents());

        if (PaymentService.isAuthorized(payment)) {
            kafka.send(Topics.PAYMENT_AUTHORIZED, event.orderId(), new PaymentAuthorized(
                    payment.orderId(), payment.paymentId(), payment.amountCents()));
        } else {
            log.info("declining order {}: {}", event.orderId(), payment.reason());
            kafka.send(Topics.PAYMENT_DECLINED, event.orderId(), new PaymentDeclined(
                    payment.orderId(), payment.paymentId(), payment.amountCents(), payment.reason()));
        }
    }
}
