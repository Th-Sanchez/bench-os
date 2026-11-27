package com.benchos.api.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read-only snapshot of a per-tenant feature module configuration.
 */
public record TenantModuleSnapshot(
        UUID tenantId,
        String moduleKey,
        boolean enabled,
        String configJson,
        Instant updatedAt
) {
}
