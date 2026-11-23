CREATE TABLE item_photos (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_order_item_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_item_photos_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_item_photos_item FOREIGN KEY (service_order_item_id) REFERENCES service_order_items (id),
    CONSTRAINT uq_item_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_item_photos_stage CHECK (stage IN ('INTAKE', 'DIAGNOSIS', 'COMPLETED'))
);

CREATE INDEX idx_item_photos_tenant_item
    ON item_photos (tenant_id, service_order_item_id, created_at ASC);
