-- Telemetry store, written by the Telemetry Processor consumer group.

CREATE TABLE telemetry_events (
    id                 BIGSERIAL     PRIMARY KEY,
    event_id           VARCHAR(100)  NOT NULL,
    correlation_id     VARCHAR(100),
    trace_id           VARCHAR(100),
    occurred_at        TIMESTAMPTZ   NOT NULL,
    service            VARCHAR(100)  NOT NULL,
    environment        VARCHAR(50)   NOT NULL,
    event_type         VARCHAR(50)   NOT NULL,
    endpoint           VARCHAR(255),
    status_code        INTEGER,
    latency_ms         INTEGER,
    message            TEXT,
    deployment_version VARCHAR(100),
    received_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_telemetry_event_id UNIQUE (event_id)
);

-- The evidence collector always asks "what did service X look like between
-- T1 and T2", so service + time is the driving index.
CREATE INDEX idx_telemetry_service_time ON telemetry_events (service, occurred_at DESC);
CREATE INDEX idx_telemetry_occurred_at  ON telemetry_events (occurred_at DESC);
CREATE INDEX idx_telemetry_correlation  ON telemetry_events (correlation_id);
