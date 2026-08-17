package com.demo.orderplatform.payment.api;

import com.demo.orderplatform.payment.domain.PaymentRecord;
import com.demo.orderplatform.payment.service.PaymentService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentRecord> byOrder(@PathVariable UUID orderId) {
        return payments.find(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
