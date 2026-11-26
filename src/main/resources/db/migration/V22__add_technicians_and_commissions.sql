ALTER TABLE tenant_settings
    ADD COLUMN technician_assignment_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN commission_basis              VARCHAR(20) NOT NULL DEFAULT 'LABOR_ONLY'
        CHECK (commission_basis IN ('LABOR_ONLY', 'TOTAL_PRICE', 'NET_PROFIT'));

ALTER TABLE users
    ADD COLUMN commission_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00
        CHECK (commission_percent >= 0 AND commission_percent <= 100);

ALTER TABLE service_order_items
    ADD COLUMN assigned_technician_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN commission_percent     NUMERIC(5, 2)
        CHECK (commission_percent IS NULL OR (commission_percent >= 0 AND commission_percent <= 100)),
    ADD COLUMN commission_amount      NUMERIC(10, 2)
        CHECK (commission_amount IS NULL OR commission_amount >= 0);

CREATE INDEX idx_service_order_items_tenant_tech
    ON service_order_items (tenant_id, assigned_technician_id)
    WHERE assigned_technician_id IS NOT NULL;
