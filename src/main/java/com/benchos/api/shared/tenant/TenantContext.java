package com.benchos.api.shared.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the authenticated tenant identifier for the current request thread.
 * Must always be cleared in a finally block by the HTTP filter that populates it.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID must not be null");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> getCurrentTenant() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID requireCurrentTenant() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context is bound to the current thread");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
