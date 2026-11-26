ALTER TABLE service_order_items
    ADD COLUMN quote_sent_at TIMESTAMPTZ,
    ADD COLUMN rejected_at   TIMESTAMPTZ;

CREATE INDEX idx_service_order_items_tenant_created_at
    ON service_order_items (tenant_id, created_at DESC);

CREATE INDEX idx_service_order_items_tenant_delivered_at
    ON service_order_items (tenant_id, delivered_at DESC)
    WHERE delivered_at IS NOT NULL;
