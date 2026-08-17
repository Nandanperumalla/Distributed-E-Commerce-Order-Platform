CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_id    VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    total_cents    BIGINT       NOT NULL CHECK (total_cents >= 0),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    order_id         UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku              VARCHAR(64) NOT NULL,
    quantity         INT         NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT      NOT NULL CHECK (unit_price_cents >= 0),
    PRIMARY KEY (order_id, sku)
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
