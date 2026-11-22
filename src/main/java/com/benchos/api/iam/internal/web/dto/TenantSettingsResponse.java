package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.internal.entity.TenantSettings;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantSettingsResponse(
        UUID tenantId,
        BigDecimal defaultLaborCost,
        int defaultEstimatedDays,
        int abandonmentDays,
        String whatsappQuoteTemplate
) {
    public static TenantSettingsResponse from(TenantSettings settings) {
        return new TenantSettingsResponse(
                settings.getTenantId(),
                settings.getDefaultLaborCost(),
                settings.getDefaultEstimatedDays(),
                settings.getAbandonmentDays(),
                settings.getWhatsappQuoteTemplate()
        );
    }
}
