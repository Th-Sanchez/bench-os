package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTenantSettingsRequest(
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal defaultLaborCost,
        @NotNull @Min(0) Integer defaultEstimatedDays,
        @NotNull @Min(1) Integer abandonmentDays,
        String whatsappQuoteTemplate,
        String whatsappIntakeTemplate,
        String whatsappReadyTemplate,
        @Size(max = 500) String googleReviewUrl,
        Boolean deliverySignatureEnabled
) {
}
