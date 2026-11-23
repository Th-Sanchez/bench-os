package com.benchos.api.order.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tenant_sequences")
public class TenantSequence {

    public static final long INITIAL_LAST_ORDER_NUMBER = 1000L;

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "last_order_number", nullable = false)
    private long lastOrderNumber;

    protected TenantSequence() {
    }

    public TenantSequence(UUID tenantId) {
        this.tenantId = tenantId;
        this.lastOrderNumber = INITIAL_LAST_ORDER_NUMBER;
    }

    public long nextOrderNumber() {
        this.lastOrderNumber += 1;
        return this.lastOrderNumber;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public long getLastOrderNumber() {
        return lastOrderNumber;
    }
}
