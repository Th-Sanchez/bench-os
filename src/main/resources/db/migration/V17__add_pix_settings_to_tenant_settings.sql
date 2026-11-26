ALTER TABLE tenant_settings
    ADD COLUMN pix_key VARCHAR(120),
    ADD COLUMN pix_receiver_name VARCHAR(60),
    ADD COLUMN pix_receiver_city VARCHAR(60);
