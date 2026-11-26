package com.benchos.api.iam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public read-only view of a tenant's operational settings exposed to other application modules.
 */
public record TenantSettingsSnapshot(
        UUID tenantId,
        BigDecimal defaultLaborCost,
        int defaultEstimatedDays,
        int abandonmentDays,
        String whatsappIntakeTemplate,
        String whatsappQuoteTemplate,
        String whatsappReadyTemplate,
        List<String> customApplianceTypes,
        String googleReviewUrl,
        boolean deliverySignatureEnabled,
        Map<String, List<String>> intakeChecklistTemplates,
        String pixKey,
        String pixReceiverName,
        String pixReceiverCity
) {
}
