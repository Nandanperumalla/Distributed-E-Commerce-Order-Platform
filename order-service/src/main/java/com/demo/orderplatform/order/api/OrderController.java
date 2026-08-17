package com.demo.orderplatform.order.api;

import com.demo.orderplatform.order.domain.OrderSnapshot;
import com.demo.orderplatform.order.service.OrderService;
import com.demo.orderplatform.order.service.PlacedOrder;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * 202, not 201: the order exists but it is not fulfilled yet. Poll
     * {@code GET /orders/{id}} to watch the saga settle it.
     */
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> place(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {

        // No key means the caller has opted out of dedupe; give them a unique one
        // rather than silently collapsing unrelated orders together.
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        PlacedOrder placed = orders.place(key, request);
        PlaceOrderResponse body = new PlaceOrderResponse(placed.orderId(), "PENDING", placed.replayed());

        if (placed.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.accepted()
                .location(URI.create("/orders/" + placed.orderId()))
                .body(body);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderSnapshot> get(@PathVariable UUID orderId) {
        return orders.find(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderSnapshot> recent(@RequestParam(defaultValue = "20") int limit) {
        return orders.recent(Math.clamp(limit, 1, 200));
    }
}
