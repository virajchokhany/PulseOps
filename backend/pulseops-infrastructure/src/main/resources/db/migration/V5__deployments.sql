-- Deployment metadata. Seeded for the MVP rather than pulled from a real CI/CD
-- system; the AI worker only cares that "a deploy happened shortly before the
-- incident", not where the record came from.

CREATE TABLE deployments (
    id           BIGSERIAL    PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL REFERENCES services (name) ON DELETE CASCADE,
    version      VARCHAR(50)  NOT NULL,
    commit_sha   VARCHAR(64)  NOT NULL,
    environment  VARCHAR(50)  NOT NULL,
    deployed_by  VARCHAR(150),
    deployed_at  TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'SUCCEEDED',
    notes        TEXT,
    CONSTRAINT uq_deployment UNIQUE (service_name, version, environment)
);

CREATE INDEX idx_deployments_service_time ON deployments (service_name, deployed_at DESC);
CREATE INDEX idx_deployments_commit       ON deployments (commit_sha);
