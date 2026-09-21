-- Alert and incident state. The Incident Store consumer group owns `incidents`,
-- `incident_services` and `incident_timeline`; the Alert Engine owns `alerts`.

CREATE TABLE alerts (
    alert_id       VARCHAR(100) PRIMARY KEY,
    correlation_id VARCHAR(100),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    service        VARCHAR(100) NOT NULL,
    alert_type     VARCHAR(100) NOT NULL,
    severity       VARCHAR(20)  NOT NULL,
    description    TEXT         NOT NULL,
    metric_value   DOUBLE PRECISION,
    threshold      DOUBLE PRECISION,
    window_seconds INTEGER,
    sample_count   INTEGER,
    incident_key   UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_service_time ON alerts (service, occurred_at DESC);
CREATE INDEX idx_alerts_incident     ON alerts (incident_key);

-- Human-facing incident numbers start at 1042 so the demo matches the design doc.
CREATE SEQUENCE incident_number_seq START WITH 1042 INCREMENT BY 1;

-- incident_key is a UUID minted by the Incident Correlation engine, NOT a
-- database identity. Correlation must be able to name an incident in the
-- `incidents` Kafka event before the Incident Store has committed anything.
CREATE TABLE incidents (
    incident_key    UUID         PRIMARY KEY,
    incident_number BIGINT       NOT NULL DEFAULT nextval('incident_number_seq'),
    title           VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    primary_service VARCHAR(100) NOT NULL,
    correlation_id  VARCHAR(100),
    alert_count     INTEGER      NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    rca_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    current_rca_id  BIGINT,
    opened_at       TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT uq_incident_number UNIQUE (incident_number)
);

CREATE INDEX idx_incidents_status          ON incidents (status);
CREATE INDEX idx_incidents_opened_at       ON incidents (opened_at DESC);
CREATE INDEX idx_incidents_primary_service ON incidents (primary_service);

CREATE TABLE incident_services (
    incident_key UUID         NOT NULL REFERENCES incidents (incident_key) ON DELETE CASCADE,
    service_name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_incident_services PRIMARY KEY (incident_key, service_name)
);

CREATE TABLE incident_timeline (
    id             BIGSERIAL    PRIMARY KEY,
    incident_key   UUID         NOT NULL REFERENCES incidents (incident_key) ON DELETE CASCADE,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    entry_type     VARCHAR(50)  NOT NULL,
    summary        TEXT         NOT NULL,
    detail         JSONB,
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_timeline_incident ON incident_timeline (incident_key, occurred_at);

-- Deliberately NO foreign key from alerts.incident_key to incidents.
-- The Alert Engine and the Incident Store are independent consumer groups, so
-- an alert row can exist before its incident row is committed. A FK here would
-- turn a normal eventual-consistency window into a hard write failure.
