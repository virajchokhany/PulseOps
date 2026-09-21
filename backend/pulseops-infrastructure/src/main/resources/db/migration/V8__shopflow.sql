-- ShopFlow is the demo application PulseOps observes. It is NOT part of the
-- platform, but it shares the local database to keep the MVP to one Postgres.

CREATE TABLE orders (
    id             UUID           PRIMARY KEY,
    customer_id    VARCHAR(100)   NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'USD',
    status         VARCHAR(20)    NOT NULL,
    payment_id     UUID,
    failure_reason TEXT,
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
CREATE INDEX idx_orders_status     ON orders (status);

CREATE TABLE payments (
    id                 UUID           PRIMARY KEY,
    order_id           UUID           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL DEFAULT 'USD',
    status             VARCHAR(20)    NOT NULL,
    provider_reference VARCHAR(100),
    failure_reason     TEXT,
    correlation_id     VARCHAR(100),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order      ON payments (order_id);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
