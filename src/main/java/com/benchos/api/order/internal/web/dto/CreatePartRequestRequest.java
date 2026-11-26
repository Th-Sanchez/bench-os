package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePartRequestRequest(
        UUID partCatalogId,
        @Size(max = 150)
        String partName,
        @Min(1)
        @Max(999)
        Integer quantity,
        @DecimalMin("0.00")
        BigDecimal estimatedUnitCost,
        @Size(max = 120)
        String supplierName,
        @Size(max = 500)
        String purchaseUrl,
        @Size(max = 400)
        String notes,
        Boolean moveItemToWaitingParts
) {
}
