CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE UNIQUE INDEX uq_suppliers_tenant_name_active
    ON suppliers (tenant_id, LOWER(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_suppliers_tenant_deleted_at
    ON suppliers (tenant_id, deleted_at);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    service_order_item_id UUID,
    supplier_id UUID,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_date DATE,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_financial_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_financial_transactions_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT ck_financial_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_financial_transactions_category CHECK (category IN (
        'REPAIR_PAYMENT',
        'PARTS_PURCHASE',
        'OPERATIONAL_EXPENSE',
        'OTHER'
    )),
    CONSTRAINT ck_financial_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_financial_transactions_payment_method CHECK (payment_method IN (
        'PIX',
        'CREDIT_CARD',
        'DEBIT_CARD',
        'CASH',
        'STORE_CREDIT'
    )),
    CONSTRAINT ck_financial_transactions_status CHECK (status IN ('PENDING', 'PAID')),
    CONSTRAINT ck_financial_transactions_store_credit_supplier CHECK (
        payment_method <> 'STORE_CREDIT' OR supplier_id IS NOT NULL
    )
);

CREATE INDEX idx_financial_transactions_tenant_created_at
    ON financial_transactions (tenant_id, created_at DESC);

CREATE INDEX idx_financial_transactions_tenant_type_status
    ON financial_transactions (tenant_id, type, status);

CREATE INDEX idx_financial_transactions_tenant_due_date_pending
    ON financial_transactions (tenant_id, due_date ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_financial_transactions_tenant_item
    ON financial_transactions (tenant_id, service_order_item_id)
    WHERE service_order_item_id IS NOT NULL;
