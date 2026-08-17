CREATE TABLE inventory (
    sku         VARCHAR(64) PRIMARY KEY,
    name        TEXT        NOT NULL,
    price_cents BIGINT      NOT NULL CHECK (price_cents >= 0),
    -- The CHECK is the backstop. Even if a future code path forgets the
    -- conditional UPDATE, the database will refuse to go negative.
    available   INT         NOT NULL CHECK (available >= 0),
    reserved    INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version     BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservations (
    order_id   UUID PRIMARY KEY,
    status     VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_items (
    order_id UUID        NOT NULL REFERENCES reservations (order_id) ON DELETE CASCADE,
    sku      VARCHAR(64) NOT NULL,
    quantity INT         NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, sku)
);
