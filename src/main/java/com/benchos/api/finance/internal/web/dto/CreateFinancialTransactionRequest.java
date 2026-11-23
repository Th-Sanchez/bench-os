package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFinancialTransactionRequest(
        @NotNull TransactionType type,
        @NotNull TransactionCategory category,
        UUID serviceOrderItemId,
        UUID supplierId,
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull TransactionPaymentMethod paymentMethod,
        TransactionStatus status,
        LocalDate dueDate
) {
}
