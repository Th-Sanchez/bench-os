package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuoteOptionRequest(
        @NotBlank @Size(max = 120) String label,
        @Size(max = 300) String description,
        @NotNull @DecimalMin("0.00") BigDecimal laborCost,
        @NotNull @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal partsCostActual,
        @DecimalMin("0.00") BigDecimal discount,
        @Min(0) Integer warrantyDays,
        Boolean recommended
) {
}
