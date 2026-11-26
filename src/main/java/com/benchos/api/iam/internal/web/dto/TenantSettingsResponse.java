package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.internal.entity.TenantSettings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
        boolean deliverySignatureEnabled,
        Map<String, List<String>> intakeChecklistTemplates,
        String pixKey,
        String pixReceiverName,
        String pixReceiverCity,
        String shopLogoDataUrl,
        String shopAddress,
        String businessHours,
        String warrantyTerms,
        Map<String, List<String>> careTipsTemplates
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
                settings.isDeliverySignatureEnabled(),
                settings.getIntakeChecklistTemplateMap(),
                settings.getPixKey(),
                settings.getPixReceiverName(),
                settings.getPixReceiverCity(),
                settings.getShopLogoDataUrl(),
                settings.getShopAddress(),
                settings.getBusinessHours(),
                settings.getWarrantyTerms(),
                settings.getCareTipsTemplateMap()
        );
    }
}
