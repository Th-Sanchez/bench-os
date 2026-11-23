CREATE TABLE customers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    document VARCHAR(30),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE UNIQUE INDEX uq_customers_tenant_phone_active
    ON customers (tenant_id, phone)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_customers_tenant_deleted_at
    ON customers (tenant_id, deleted_at);

CREATE INDEX idx_customers_tenant_name_active
    ON customers (tenant_id, LOWER(name))
    WHERE deleted_at IS NULL;
