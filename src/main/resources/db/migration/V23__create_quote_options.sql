CREATE TABLE service_order_item_quote_options (
    id                    UUID           PRIMARY KEY,
    tenant_id             UUID           NOT NULL REFERENCES tenants (id),
    service_order_item_id UUID           NOT NULL REFERENCES service_order_items (id) ON DELETE CASCADE,
    option_index          INT            NOT NULL CHECK (option_index >= 1),
    label                 VARCHAR(120)   NOT NULL,
    description           VARCHAR(300),
    labor_cost            NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (labor_cost >= 0),
    parts_cost            NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (parts_cost >= 0),
    parts_cost_actual     NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (parts_cost_actual >= 0),
    discount              NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (discount >= 0),
    total_price           NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (total_price >= 0),
    warranty_days         INT            NOT NULL DEFAULT 90 CHECK (warranty_days >= 0),
    recommended           BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_quote_options_item_index UNIQUE (service_order_item_id, option_index)
);

CREATE INDEX idx_quote_options_tenant_item
    ON service_order_item_quote_options (tenant_id, service_order_item_id, option_index ASC);

ALTER TABLE service_order_items
    ADD COLUMN selected_quote_option_id    UUID REFERENCES service_order_item_quote_options (id) ON DELETE SET NULL,
    ADD COLUMN selected_quote_option_label VARCHAR(120);
