package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.UUID;

public record AssignTechnicianRequest(
        UUID technicianId,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPercent,
        Boolean clearCommissionOverride
) {
}
