package com.demo.orderplatform.inventory.store;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.inventory.domain.StockItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The whole anti-overselling story is this one statement.
     *
     * <p>{@code available >= ?} lives in the WHERE clause, not in Java. Postgres
     * takes a row lock for the duration of the UPDATE, so two concurrent
     * transactions chasing the last unit are serialised by the database: the
     * second one re-evaluates the predicate against the already-decremented row
     * and matches nothing. Reading the count first and deciding in application
     * code is precisely the race this avoids.
     *
     * @return 1 if stock was taken, 0 if there was not enough (or no such SKU)
     */
    public int tryDecrement(String sku, int quantity) {
        return jdbc.update("""
                UPDATE inventory
                   SET available = available - ?,
                       reserved  = reserved + ?,
                       version   = version + 1,
                       updated_at = now()
                 WHERE sku = ?
                   AND available >= ?
                """, quantity, quantity, sku, quantity);
    }

    public int restore(String sku, int quantity) {
        return jdbc.update("""
                UPDATE inventory
                   SET available = available + ?,
                       reserved  = reserved - ?,
                       version   = version + 1,
                       updated_at = now()
                 WHERE sku = ?
                """, quantity, quantity, sku);
    }

    /**
     * @return true if this call created the reservation; false if some earlier
     *         delivery of the same event already did
     */
    public boolean claimReservation(UUID orderId) {
        return jdbc.update("""
                INSERT INTO reservations (order_id, status)
                VALUES (?, 'RESERVED')
                ON CONFLICT (order_id) DO NOTHING
                """, orderId) == 1;
    }

    public Optional<String> reservationStatus(UUID orderId) {
        List<String> rows = jdbc.queryForList(
                "SELECT status FROM reservations WHERE order_id = ?", String.class, orderId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void saveReservationLines(UUID orderId, List<OrderLine> lines) {
        jdbc.batchUpdate("""
                INSERT INTO reservation_items (order_id, sku, quantity)
                VALUES (?, ?, ?)
                ON CONFLICT (order_id, sku) DO NOTHING
                """, lines.stream()
                .map(line -> new Object[]{orderId, line.sku(), line.quantity()})
                .toList());
    }

    /** Guarded transition, so a redelivered release cannot restock twice. */
    public boolean markReleased(UUID orderId) {
        return jdbc.update("""
                UPDATE reservations SET status = 'RELEASED', updated_at = now()
                 WHERE order_id = ? AND status = 'RESERVED'
                """, orderId) == 1;
    }

    public List<OrderLine> reservationLines(UUID orderId) {
        return jdbc.query("""
                SELECT sku, quantity FROM reservation_items WHERE order_id = ? ORDER BY sku
                """, (rs, rowNum) -> new OrderLine(rs.getString("sku"), rs.getInt("quantity"), 0L), orderId);
    }

    public boolean exists(String sku) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory WHERE sku = ?", Integer.class, sku);
        return count != null && count > 0;
    }

    public Optional<StockItem> find(String sku) {
        List<StockItem> rows = jdbc.query("""
                SELECT sku, name, available, price_cents FROM inventory WHERE sku = ?
                """, (rs, rowNum) -> new StockItem(
                rs.getString("sku"),
                rs.getString("name"),
                rs.getInt("available"),
                rs.getLong("price_cents")), sku);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<StockItem> findAll() {
        return jdbc.query("""
                SELECT sku, name, available, price_cents FROM inventory ORDER BY sku
                """, (rs, rowNum) -> new StockItem(
                rs.getString("sku"),
                rs.getString("name"),
                rs.getInt("available"),
                rs.getLong("price_cents")));
    }
}
