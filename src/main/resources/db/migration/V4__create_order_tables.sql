CREATE TABLE tenant_sequences (
    tenant_id UUID PRIMARY KEY,
    last_order_number BIGINT NOT NULL DEFAULT 1000,

    CONSTRAINT fk_tenant_sequences_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_tenant_sequences_last_order_number CHECK (last_order_number >= 1000)
);

CREATE TABLE service_orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    order_number BIGINT NOT NULL,
    public_token UUID NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_service_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_service_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_service_orders_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_service_orders_tenant_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uq_service_orders_public_token UNIQUE (public_token),
    CONSTRAINT ck_service_orders_order_number CHECK (order_number > 0)
);

CREATE INDEX idx_service_orders_tenant_created_at
    ON service_orders (tenant_id, created_at DESC);

CREATE INDEX idx_service_orders_tenant_customer
    ON service_orders (tenant_id, customer_id);

CREATE TABLE service_order_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    item_index INT NOT NULL,
    appliance_type VARCHAR(80) NOT NULL,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(120),
    voltage VARCHAR(20) NOT NULL,
    serial_number VARCHAR(100),
    accessories TEXT,
    reported_issue TEXT NOT NULL,
    technical_diagnosis TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    labor_cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    parts_cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    discount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    estimated_completion_date DATE,
    warranty_days INT NOT NULL DEFAULT 90,
    cancellation_reason TEXT,
    approved_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    discarded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_service_order_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_service_order_items_order FOREIGN KEY (service_order_id) REFERENCES service_orders (id),
    CONSTRAINT uq_service_order_items_order_index UNIQUE (service_order_id, item_index),
    CONSTRAINT ck_service_order_items_index CHECK (item_index > 0),
    CONSTRAINT ck_service_order_items_voltage CHECK (voltage IN ('V110', 'V220', 'BIVOLT', 'BATTERY', 'UNKNOWN')),
    CONSTRAINT ck_service_order_items_status CHECK (status IN (
        'RECEIVED',
        'DIAGNOSING',
        'QUOTE_SENT',
        'APPROVED',
        'WAITING_PARTS',
        'IN_REPAIR',
        'READY_FOR_PICKUP',
        'DELIVERED',
        'REJECTED',
        'DISCARDED',
        'CANCELLED'
    )),
    CONSTRAINT ck_service_order_items_labor_cost CHECK (labor_cost >= 0),
    CONSTRAINT ck_service_order_items_parts_cost CHECK (parts_cost >= 0),
    CONSTRAINT ck_service_order_items_discount CHECK (discount >= 0),
    CONSTRAINT ck_service_order_items_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_service_order_items_warranty_days CHECK (warranty_days >= 0)
);

CREATE INDEX idx_service_order_items_tenant_status
    ON service_order_items (tenant_id, status);

CREATE INDEX idx_service_order_items_order_id
    ON service_order_items (service_order_id);
