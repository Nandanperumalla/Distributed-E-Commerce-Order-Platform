package com.demo.orderplatform.payment.store;

import com.demo.orderplatform.payment.domain.PaymentRecord;
import com.demo.orderplatform.payment.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * order_id is the primary key, which is what makes charging a card twice for
     * one order a database error rather than a customer complaint.
     *
     * @return true if this call recorded the payment
     */
    public boolean insertIfAbsent(UUID orderId, UUID paymentId, PaymentStatus status,
                                  long amountCents, String reason) {
        return jdbc.update("""
                INSERT INTO payments (order_id, payment_id, status, amount_cents, reason)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (order_id) DO NOTHING
                """, orderId, paymentId, status.name(), amountCents, reason) == 1;
    }

    public Optional<PaymentRecord> find(UUID orderId) {
        List<PaymentRecord> rows = jdbc.query("""
                SELECT order_id, payment_id, status, amount_cents, reason, created_at
                FROM payments WHERE order_id = ?
                """, (rs, rowNum) -> new PaymentRecord(
                rs.getString("order_id"),
                rs.getString("payment_id"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getLong("amount_cents"),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()), orderId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
