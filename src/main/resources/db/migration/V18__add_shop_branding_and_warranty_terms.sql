ALTER TABLE tenant_settings
    ADD COLUMN shop_logo_data_url TEXT,
    ADD COLUMN shop_address VARCHAR(300),
    ADD COLUMN business_hours VARCHAR(200),
    ADD COLUMN warranty_terms TEXT;
