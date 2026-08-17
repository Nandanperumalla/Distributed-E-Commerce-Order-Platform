package com.demo.orderplatform.inventory.service;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.inventory.domain.InsufficientStockException;
import com.demo.orderplatform.inventory.store.InventoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    public enum Outcome {
        /** This call took the stock. */
        RESERVED,
        /** An earlier delivery of the same event already took it. */
        ALREADY_RESERVED,
        /** Reserved once, then released by a compensating command. Do nothing. */
        ALREADY_RELEASED
    }

    private final InventoryRepository repository;

    public ReservationService(InventoryRepository repository) {
        this.repository = repository;
    }

    /**
     * All-or-nothing across every line in the order.
     *
     * <p>Two things make this safe under load. The reservation row is claimed
     * with {@code ON CONFLICT DO NOTHING}, so a redelivered Kafka record cannot
     * decrement stock a second time. And each line is taken with a conditional
     * UPDATE, so concurrent orders for the last unit are resolved by Postgres
     * rather than by whoever read the row first.
     *
     * @throws InsufficientStockException rolls the whole transaction back,
     *         including any lines already decremented and the reservation itself
     */
    @Transactional
    public Outcome reserve(String orderId, List<OrderLine> lines) {
        UUID id = UUID.fromString(orderId);

        if (!repository.claimReservation(id)) {
            String status = repository.reservationStatus(id).orElse("RESERVED");
            return "RELEASED".equals(status) ? Outcome.ALREADY_RELEASED : Outcome.ALREADY_RESERVED;
        }

        // Consistent lock order across transactions. Two orders touching
        // {A, B} and {B, A} at the same moment would otherwise deadlock.
        List<OrderLine> ordered = lines.stream()
                .sorted(Comparator.comparing(OrderLine::sku))
                .toList();

        repository.saveReservationLines(id, ordered);

        for (OrderLine line : ordered) {
            if (repository.tryDecrement(line.sku(), line.quantity()) == 0) {
                throw new InsufficientStockException(line.sku(), explain(line));
            }
        }
        return Outcome.RESERVED;
    }

    /**
     * Compensation for a declined payment.
     *
     * @return the lines that were put back, or empty if this release already ran
     */
    @Transactional
    public List<OrderLine> release(String orderId) {
        UUID id = UUID.fromString(orderId);
        if (!repository.markReleased(id)) {
            return List.of();
        }
        List<OrderLine> lines = repository.reservationLines(id);
        for (OrderLine line : lines) {
            repository.restore(line.sku(), line.quantity());
        }
        return lines;
    }

    private String explain(OrderLine line) {
        return repository.find(line.sku())
                .map(item -> "wanted " + line.quantity() + ", only " + item.available() + " available")
                .orElse("no such sku in the catalog");
    }
}
