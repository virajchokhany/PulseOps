-- Service Catalog: lets PulseOps reason about ownership and blast radius
-- (order-service depends on payment-service, so payment errors explain order errors).

CREATE TABLE services (
    name         VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(150) NOT NULL,
    owner        VARCHAR(150) NOT NULL,
    repository   VARCHAR(255) NOT NULL,
    source_path  VARCHAR(255),
    environment  VARCHAR(50)  NOT NULL,
    version      VARCHAR(50),
    tier         VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE service_dependencies (
    service_name    VARCHAR(100) NOT NULL REFERENCES services (name) ON DELETE CASCADE,
    depends_on_name VARCHAR(100) NOT NULL REFERENCES services (name) ON DELETE CASCADE,
    description     VARCHAR(255),
    CONSTRAINT pk_service_dependencies PRIMARY KEY (service_name, depends_on_name),
    CONSTRAINT chk_no_self_dependency CHECK (service_name <> depends_on_name)
);

-- Reverse lookup: "who breaks when payment-service breaks?"
CREATE INDEX idx_service_dependencies_reverse ON service_dependencies (depends_on_name);

CREATE TABLE runbooks (
    id           BIGSERIAL    PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL REFERENCES services (name) ON DELETE CASCADE,
    alert_type   VARCHAR(100),
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_runbooks_service ON runbooks (service_name, alert_type);
