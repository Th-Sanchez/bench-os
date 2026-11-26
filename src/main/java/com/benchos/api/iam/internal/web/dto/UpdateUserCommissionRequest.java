package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateUserCommissionRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPercent
) {
}
