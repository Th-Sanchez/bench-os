package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpsertPartCatalogRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String applianceCategory,
        @NotNull @DecimalMin("0.00") BigDecimal averageCost,
        @NotNull @DecimalMin("0.00") BigDecimal suggestedPrice,
        @Size(max = 300) String notes
) {
}
