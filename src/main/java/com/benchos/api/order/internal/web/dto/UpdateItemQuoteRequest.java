package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateItemQuoteRequest(
        @Size(max = 2000) String technicalDiagnosis,
        @NotNull @DecimalMin("0.00") BigDecimal laborCost,
        @NotNull @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal partsCostActual,
        @NotNull @DecimalMin("0.00") BigDecimal discount,
        @Min(0) Integer warrantyDays
) {
}
