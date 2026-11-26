package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePartRequestRequest(
        @NotBlank
        @Size(max = 150)
        String partName,
        @Min(1)
        @Max(999)
        Integer quantity,
        @DecimalMin("0.00")
        BigDecimal estimatedUnitCost,
        @DecimalMin("0.00")
        BigDecimal actualUnitCost,
        @Size(max = 120)
        String supplierName,
        @Size(max = 500)
        String purchaseUrl,
        @Size(max = 400)
        String notes
) {
}
