package com.benchos.api.integration.internal.entity;

import com.benchos.api.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_modules")
public class TenantModule {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "module_key", nullable = false, length = 64, updatable = false)
    private String moduleKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantModule() {
    }

    public TenantModule(UUID tenantId, String moduleKey, boolean enabled, String configJson, Instant now) {
        this.id = UuidV7Generator.next();
        this.tenantId = tenantId;
        this.moduleKey = moduleKey;
        this.enabled = enabled;
        this.configJson = (configJson == null || configJson.isBlank()) ? "{}" : configJson;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(boolean enabled, String configJson, Instant now) {
        this.enabled = enabled;
        if (configJson != null) {
            this.configJson = configJson.isBlank() ? "{}" : configJson;
        }
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getConfigJson() {
        return configJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
