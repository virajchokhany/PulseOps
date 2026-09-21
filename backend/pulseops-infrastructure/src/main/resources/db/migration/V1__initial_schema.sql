-- Shared plumbing used by every Kafka consumer in PulseOps.

-- Idempotency ledger. Kafka gives at-least-once delivery, so a consumer can
-- legitimately see the same event twice (rebalance, retry, offset replay).
-- Each consumer records the event ids it has already applied and skips repeats.
-- Keyed by (consumer, event_id) because the same event is processed once per
-- consumer group, and those are genuinely independent pieces of work.
CREATE TABLE processed_events (
    consumer     VARCHAR(100) NOT NULL,
    event_id     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer, event_id)
);

-- Supports periodic pruning of the ledger.
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
