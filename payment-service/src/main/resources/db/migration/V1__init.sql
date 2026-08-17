CREATE TABLE payments (
    -- One payment per order, enforced by the primary key. A redelivered
    -- inventory.reserved event cannot charge the same card twice.
    order_id     UUID PRIMARY KEY,
    payment_id   UUID        NOT NULL,
    status       VARCHAR(16) NOT NULL CHECK (status IN ('AUTHORIZED', 'DECLINED')),
    amount_cents BIGINT      NOT NULL CHECK (amount_cents >= 0),
    reason       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_status ON payments (status);
