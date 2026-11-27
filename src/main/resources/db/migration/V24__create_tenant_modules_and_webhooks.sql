CREATE TABLE tenant_modules (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    module_key  VARCHAR(64)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    config_json TEXT         NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tenant_modules_tenant_key UNIQUE (tenant_id, module_key)
);

CREATE INDEX idx_tenant_modules_tenant
    ON tenant_modules (tenant_id);

CREATE TABLE webhook_endpoints (
    id             UUID         PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    target_url     VARCHAR(500) NOT NULL,
    signing_secret VARCHAR(128) NOT NULL,
    events_csv     VARCHAR(400) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_endpoints_tenant_active
    ON webhook_endpoints (tenant_id, active);

CREATE TABLE webhook_deliveries (
    id                  UUID         PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    webhook_endpoint_id UUID         NOT NULL REFERENCES webhook_endpoints (id) ON DELETE CASCADE,
    event_type          VARCHAR(64)  NOT NULL,
    event_id            UUID         NOT NULL,
    payload_json        TEXT         NOT NULL,
    status              VARCHAR(24)  NOT NULL,
    http_status         INT,
    duration_ms         INT,
    response_snippet    VARCHAR(500),
    error_message       VARCHAR(500),
    attempt_count       INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMPTZ
);

CREATE INDEX idx_webhook_deliveries_endpoint_created
    ON webhook_deliveries (tenant_id, webhook_endpoint_id, created_at DESC);
