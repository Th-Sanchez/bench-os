CREATE TABLE parts_catalog (
    id                 UUID           PRIMARY KEY,
    tenant_id          UUID           NOT NULL REFERENCES tenants (id),
    name               VARCHAR(120)   NOT NULL,
    appliance_category VARCHAR(80),
    average_cost       NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (average_cost >= 0),
    suggested_price    NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (suggested_price >= 0),
    notes              VARCHAR(300),
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_parts_catalog_tenant_name_active
    ON parts_catalog (tenant_id, LOWER(name))
    WHERE active = TRUE;

CREATE INDEX idx_parts_catalog_tenant_active_name
    ON parts_catalog (tenant_id, active, name);

ALTER TABLE service_order_items
    ADD COLUMN parts_cost_actual NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (parts_cost_actual >= 0);
