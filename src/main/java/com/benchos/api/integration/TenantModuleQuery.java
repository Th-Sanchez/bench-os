package com.benchos.api.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Spring Modulith API allowing other modules to check tenant-specific feature flags
 * and read custom module configurations without coupling to tenant IDs in core logic.
 */
public interface TenantModuleQuery {

    boolean isEnabled(UUID tenantId, String moduleKey);

    Optional<TenantModuleSnapshot> findModule(UUID tenantId, String moduleKey);

    List<TenantModuleSnapshot> listEnabledModules(UUID tenantId);
}
