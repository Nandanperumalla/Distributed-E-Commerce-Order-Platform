package com.demo.orderplatform.order.store;

import com.demo.orderplatform.common.event.OrderLine;
import com.demo.orderplatform.order.domain.OrderSnapshot;
import com.demo.orderplatform.order.domain.OrderStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID orderId, String customerId, long totalCents, List<OrderLine> lines) {
        jdbc.update("""
                INSERT INTO orders (id, customer_id, status, total_cents)
                VALUES (?, ?, ?, ?)
                """, orderId, customerId, OrderStatus.PENDING.name(), totalCents);

        jdbc.batchUpdate("""
                INSERT INTO order_items (order_id, sku, quantity, unit_price_cents)
                VALUES (?, ?, ?, ?)
                """, lines.stream()
                .map(line -> new Object[]{orderId, line.sku(), line.quantity(), line.unitPriceCents()})
                .toList());
    }

    /**
     * Advances the saga only from a status we expect to be in. A duplicate or
     * out-of-order event matches no row and quietly changes nothing, which is
     * what makes the listeners safe to replay.
     *
     * @return true if this call was the one that moved the order
     */
    public boolean transition(UUID orderId, OrderStatus to, String failureReason, OrderStatus... allowedFrom) {
        String placeholders = String.join(",", Collections.nCopies(allowedFrom.length, "?"));
        String sql = "UPDATE orders SET status = ?, failure_reason = ?, updated_at = now() "
                + "WHERE id = ? AND status IN (" + placeholders + ")";

        List<Object> args = new ArrayList<>(3 + allowedFrom.length);
        args.add(to.name());
        args.add(failureReason);
        args.add(orderId);
        for (OrderStatus from : allowedFrom) {
            args.add(from.name());
        }
        return jdbc.update(sql, args.toArray()) == 1;
    }

    public Optional<OrderSnapshot> find(UUID orderId) {
        List<OrderSnapshot> heads = jdbc.query("""
                SELECT id, customer_id, status, total_cents, failure_reason, created_at, updated_at
                FROM orders WHERE id = ?
                """, (rs, rowNum) -> new OrderSnapshot(
                rs.getString("id"),
                rs.getString("customer_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getLong("total_cents"),
                rs.getString("failure_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                List.of()), orderId);

        if (heads.isEmpty()) {
            return Optional.empty();
        }

        List<OrderLine> lines = jdbc.query("""
                SELECT sku, quantity, unit_price_cents
                FROM order_items WHERE order_id = ? ORDER BY sku
                """, (rs, rowNum) -> new OrderLine(
                rs.getString("sku"),
                rs.getInt("quantity"),
                rs.getLong("unit_price_cents")), orderId);

        OrderSnapshot head = heads.get(0);
        return Optional.of(new OrderSnapshot(head.orderId(), head.customerId(), head.status(),
                head.totalCents(), head.failureReason(), head.createdAt(), head.updatedAt(), lines));
    }

    public List<OrderSnapshot> recent(int limit) {
        return jdbc.query("""
                SELECT id, customer_id, status, total_cents, failure_reason, created_at, updated_at
                FROM orders ORDER BY created_at DESC LIMIT ?
                """, (rs, rowNum) -> new OrderSnapshot(
                rs.getString("id"),
                rs.getString("customer_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getLong("total_cents"),
                rs.getString("failure_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                List.of()), limit);
    }
}
