ALTER TABLE tenant_settings
    ADD COLUMN intake_checklist_templates TEXT;

ALTER TABLE service_order_items
    ADD COLUMN intake_checklist TEXT;
