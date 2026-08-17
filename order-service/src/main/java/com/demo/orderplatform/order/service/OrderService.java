package com.demo.orderplatform.order.service;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.order.api.PlaceOrderRequest;
import com.demo.orderplatform.order.domain.OrderSnapshot;
import com.demo.orderplatform.order.store.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final IdempotencyKeys idempotency;
    private final CatalogClient catalog;
    private final OrderWriter writer;
    private final OrderRepository repository;

    public OrderService(IdempotencyKeys idempotency,
                        CatalogClient catalog,
                        OrderWriter writer,
                        OrderRepository repository) {
        this.idempotency = idempotency;
        this.catalog = catalog;
        this.writer = writer;
        this.repository = repository;
    }

    /**
     * Intake stops at the database write. Reserving stock and taking payment
     * happen off the Kafka topics afterwards, so a payment gateway having a bad
     * afternoon slows nothing down for the customer clicking Buy.
     */
    public PlacedOrder place(String idempotencyKey, PlaceOrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        Optional<String> alreadyPlaced = idempotency.claim(idempotencyKey, orderId);
        if (alreadyPlaced.isPresent()) {
            log.info("idempotency key {} replayed -> order {}", idempotencyKey, alreadyPlaced.get());
            return new PlacedOrder(alreadyPlaced.get(), true);
        }

        try {
            List<OrderLine> lines = priced(request);
            long totalCents = lines.stream()
                    .mapToLong(line -> (long) line.quantity() * line.unitPriceCents())
                    .sum();
            writer.create(UUID.fromString(orderId), request.customerId(), lines, totalCents);
            log.info("order {} accepted for {} ({} cents)", orderId, request.customerId(), totalCents);
            return new PlacedOrder(orderId, false);
        } catch (RuntimeException e) {
            // Nothing was written, so the key must not stay claimed or the
            // client's retry would get back an order id that does not exist.
            idempotency.release(idempotencyKey);
            throw e;
        }
    }

    public Optional<OrderSnapshot> find(UUID orderId) {
        return repository.find(orderId);
    }

    public List<OrderSnapshot> recent(int limit) {
        return repository.recent(limit);
    }

    /**
     * Resolves catalog prices and folds repeated SKUs into one line — the
     * order_items primary key is (order_id, sku), and a client sending the same
     * SKU twice means "three more of these", not "reject my order".
     */
    private List<OrderLine> priced(PlaceOrderRequest request) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (PlaceOrderRequest.Line line : request.lines()) {
            quantities.merge(line.sku(), line.quantity(), Integer::sum);
        }

        List<OrderLine> lines = new ArrayList<>(quantities.size());
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            CatalogItem item = catalog.lookup(entry.getKey());
            lines.add(new OrderLine(item.sku(), entry.getValue(), item.priceCents()));
        }
        return lines;
    }
}
