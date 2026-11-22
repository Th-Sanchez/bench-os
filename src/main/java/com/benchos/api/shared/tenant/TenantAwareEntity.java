package com.benchos.api.shared.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base class for all tenant-scoped JPA entities.
 * Automatically injects tenant_id before persist and declares the Hibernate tenant filter.
 */
@MappedSuperclass
@FilterDef(
        name = TenantAwareEntity.TENANT_FILTER,
        parameters = @ParamDef(name = TenantAwareEntity.TENANT_ID_PARAM, type = UUID.class)
)
@Filter(
        name = TenantAwareEntity.TENANT_FILTER,
        condition = "tenant_id = :tenantId"
)
public abstract class TenantAwareEntity {

    public static final String TENANT_FILTER = "tenantFilter";
    public static final String TENANT_ID_PARAM = "tenantId";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected TenantAwareEntity() {
    }

    protected TenantAwareEntity(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    protected void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    void assignTenantBeforePersist() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.requireCurrentTenant();
        }
    }
}
