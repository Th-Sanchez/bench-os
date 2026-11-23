package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentPartRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
