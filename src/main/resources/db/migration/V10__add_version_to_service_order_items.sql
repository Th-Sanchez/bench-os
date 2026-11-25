-- Optimistic locking version column for service_order_items to prevent lost updates
-- under concurrent quote decisions, status transitions, and deliveries.
ALTER TABLE service_order_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
