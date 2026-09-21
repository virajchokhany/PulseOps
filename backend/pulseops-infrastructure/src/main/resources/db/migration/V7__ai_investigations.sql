-- One row per LLM investigation attempt. Keeping every attempt (instead of
-- overwriting the incident's RCA in place) gives us RCA history for free and
-- makes failed/timed-out investigations visible rather than silent.

CREATE TABLE ai_investigations (
    id                BIGSERIAL    PRIMARY KEY,
    incident_key      UUID         NOT NULL REFERENCES incidents (incident_key) ON DELETE CASCADE,
    -- Incident version this RCA was computed from. If the incident has moved
    -- past this version, the RCA is stale and a new run is due.
    incident_version  BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    provider          VARCHAR(50),
    model             VARCHAR(100),
    attempt           INTEGER      NOT NULL DEFAULT 1,
    duration_ms       BIGINT,
    prompt_chars      INTEGER,
    correlation_id    VARCHAR(100),
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    failure_reason    TEXT,
    -- Validated structured RCA returned by the LLM.
    rca               JSONB,
    -- What evidence was actually collected, so a reviewer can tell whether a
    -- weak RCA was the model's fault or missing evidence.
    evidence_summary  JSONB
);

CREATE INDEX idx_ai_investigations_incident ON ai_investigations (incident_key, started_at DESC);
CREATE INDEX idx_ai_investigations_status   ON ai_investigations (status);

ALTER TABLE incidents
    ADD CONSTRAINT fk_incidents_current_rca
    FOREIGN KEY (current_rca_id) REFERENCES ai_investigations (id) ON DELETE SET NULL;
