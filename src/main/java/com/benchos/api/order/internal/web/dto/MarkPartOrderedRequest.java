package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MarkPartOrderedRequest(
        @Size(max = 120)
        String supplierName,
        @Size(max = 500)
        String purchaseUrl,
        @DecimalMin("0.00")
        BigDecimal actualUnitCost,
        @Size(max = 400)
        String notes
) {
}
