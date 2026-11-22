package com.benchos.api.iam;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public read-only view of a tenant's operational settings exposed to other application modules.
 */
public record TenantSettingsSnapshot(
        UUID tenantId,
        BigDecimal defaultLaborCost,
        int defaultEstimatedDays,
        int abandonmentDays,
        String whatsappQuoteTemplate
) {
}
