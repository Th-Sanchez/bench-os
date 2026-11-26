ALTER TABLE tenant_settings
    ADD COLUMN delivery_signature_enabled BOOLEAN NOT NULL DEFAULT FALSE;
