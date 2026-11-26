ALTER TABLE tenant_settings
    ADD COLUMN google_review_url VARCHAR(500);

ALTER TABLE service_order_items
    ADD COLUMN delivery_signature_svg TEXT,
    ADD COLUMN delivery_signer_name VARCHAR(120);

ALTER TABLE service_orders
    ADD COLUMN customer_rating INTEGER CHECK (customer_rating BETWEEN 1 AND 5),
    ADD COLUMN customer_feedback TEXT,
    ADD COLUMN customer_rated_at TIMESTAMPTZ;
