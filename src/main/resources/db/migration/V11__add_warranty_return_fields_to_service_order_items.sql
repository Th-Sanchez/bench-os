-- Link warranty-return service order items to their original delivered item
-- so workshops can trace active/expired warranties without distorting revenue.
ALTER TABLE service_order_items
    ADD COLUMN parent_item_id UUID REFERENCES service_order_items(id),
    ADD COLUMN is_warranty_return BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_service_order_items_parent_item_id
    ON service_order_items (tenant_id, parent_item_id)
    WHERE parent_item_id IS NOT NULL;
