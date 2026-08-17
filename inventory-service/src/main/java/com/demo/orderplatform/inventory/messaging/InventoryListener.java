package com.demo.orderplatform.inventory.messaging;

import com.demo.orderplatform.common.event.InventoryRejected;
import com.demo.orderplatform.common.event.InventoryReserved;
import com.demo.orderplatform.common.event.OrderCreated;
import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.common.event.ReleaseInventory;
import com.demo.orderplatform.common.event.Topics;
import com.demo.orderplatform.inventory.domain.InsufficientStockException;
import com.demo.orderplatform.inventory.service.ReservationService;
import com.demo.orderplatform.inventory.service.StockCatalog;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Everything here runs after its transaction has committed, so an event on the
 * bus always describes state that is already durable in Postgres.
 */
@Component
public class InventoryListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryListener.class);

    private final ReservationService reservations;
    private final StockCatalog catalog;
    private final KafkaTemplate<String, Object> kafka;

    public InventoryListener(ReservationService reservations,
                             StockCatalog catalog,
                             KafkaTemplate<String, Object> kafka) {
        this.reservations = reservations;
        this.catalog = catalog;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, containerFactory = "orderCreatedFactory")
    public void onOrderCreated(OrderCreated event) {
        try {
            ReservationService.Outcome outcome = reservations.reserve(event.orderId(), event.lines());

            if (outcome == ReservationService.Outcome.ALREADY_RELEASED) {
                log.info("order {} was already reserved and released; ignoring replay", event.orderId());
                return;
            }
            if (outcome == ReservationService.Outcome.RESERVED) {
                catalog.evict(skus(event.lines()));
                log.info("reserved stock for order {}", event.orderId());
            } else {
                log.info("order {} already reserved; re-announcing", event.orderId());
            }

            // Re-announced on a replay too: the original event may have been
            // published and then lost before order-service ever saw it.
            kafka.send(Topics.INVENTORY_RESERVED, event.orderId(), new InventoryReserved(
                    event.orderId(), event.customerId(), event.lines(), event.totalCents()));

        } catch (InsufficientStockException e) {
            log.info("rejecting order {}: {} — {}", event.orderId(), e.sku(), e.getMessage());
            kafka.send(Topics.INVENTORY_REJECTED, event.orderId(),
                    new InventoryRejected(event.orderId(), e.sku(), e.getMessage()));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_RELEASE, containerFactory = "releaseInventoryFactory")
    public void onRelease(ReleaseInventory command) {
        List<OrderLine> restored = reservations.release(command.orderId());
        if (restored.isEmpty()) {
            log.debug("nothing to release for order {}", command.orderId());
            return;
        }
        catalog.evict(skus(restored));
        log.info("released {} line(s) for order {}: {}", restored.size(), command.orderId(), command.reason());
    }

    private static List<String> skus(List<OrderLine> lines) {
        return lines.stream().map(OrderLine::sku).toList();
    }
}
