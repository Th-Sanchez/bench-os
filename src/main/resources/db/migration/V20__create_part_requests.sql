CREATE TABLE part_requests (
    id                    UUID           PRIMARY KEY,
    tenant_id             UUID           NOT NULL REFERENCES tenants (id),
    service_order_item_id UUID           NOT NULL REFERENCES service_order_items (id) ON DELETE CASCADE,
    part_catalog_id       UUID           REFERENCES parts_catalog (id) ON DELETE SET NULL,
    part_name             VARCHAR(150)   NOT NULL,
    quantity              INT            NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    estimated_unit_cost   NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (estimated_unit_cost >= 0),
    actual_unit_cost      NUMERIC(10, 2) CHECK (actual_unit_cost IS NULL OR actual_unit_cost >= 0),
    supplier_name         VARCHAR(120),
    purchase_url          VARCHAR(500),
    notes                 VARCHAR(400),
    status                VARCHAR(20)    NOT NULL DEFAULT 'TO_ORDER' CHECK (status IN ('TO_ORDER', 'ORDERED', 'ARRIVED', 'CANCELLED')),
    ordered_at            TIMESTAMPTZ,
    arrived_at            TIMESTAMPTZ,
    expense_recorded      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by_user_id    UUID           REFERENCES users (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_part_requests_tenant_status_created
    ON part_requests (tenant_id, status, created_at ASC);

CREATE INDEX idx_part_requests_tenant_item
    ON part_requests (tenant_id, service_order_item_id, created_at ASC);

ALTER TABLE financial_transactions
    ADD COLUMN part_request_id UUID;

CREATE UNIQUE INDEX ux_financial_transactions_tenant_part_request
    ON financial_transactions (tenant_id, part_request_id)
    WHERE part_request_id IS NOT NULL;
