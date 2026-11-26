package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.internal.entity.TenantSettings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TenantSettingsResponse(
        UUID tenantId,
        BigDecimal defaultLaborCost,
        int defaultEstimatedDays,
        int abandonmentDays,
        String whatsappIntakeTemplate,
        String whatsappQuoteTemplate,
        String whatsappReadyTemplate,
        List<String> customApplianceTypes,
        String googleReviewUrl,
        boolean deliverySignatureEnabled
) {
    public static TenantSettingsResponse from(TenantSettings settings) {
        return new TenantSettingsResponse(
                settings.getTenantId(),
                settings.getDefaultLaborCost(),
                settings.getDefaultEstimatedDays(),
                settings.getAbandonmentDays(),
                settings.getWhatsappIntakeTemplate(),
                settings.getWhatsappQuoteTemplate(),
                settings.getWhatsappReadyTemplate(),
                settings.getCustomApplianceTypeList(),
                settings.getGoogleReviewUrl(),
                settings.isDeliverySignatureEnabled()
        );
    }
}
