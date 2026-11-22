CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    document VARCHAR(30) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_settings (
    tenant_id UUID PRIMARY KEY,
    default_labor_cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    default_estimated_days INT NOT NULL DEFAULT 3,
    abandonment_days INT NOT NULL DEFAULT 180,
    whatsapp_quote_template TEXT,

    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_tenant_settings_labor_cost CHECK (default_labor_cost >= 0),
    CONSTRAINT ck_tenant_settings_estimated_days CHECK (default_estimated_days >= 0),
    CONSTRAINT ck_tenant_settings_abandonment_days CHECK (abandonment_days > 0)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('OWNER', 'ATTENDANT', 'TECHNICIAN')),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
