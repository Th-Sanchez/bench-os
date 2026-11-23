package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.finance.internal.entity.FinancialTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialTransactionResponse(
        UUID id,
        UUID tenantId,
        TransactionType type,
        TransactionCategory category,
        UUID serviceOrderItemId,
        UUID supplierId,
        String supplierName,
        String description,
        BigDecimal amount,
        TransactionPaymentMethod paymentMethod,
        TransactionStatus status,
        LocalDate dueDate,
        Instant paidAt,
        Instant createdAt
) {
    public static FinancialTransactionResponse from(FinancialTransaction tx, String supplierName) {
        return new FinancialTransactionResponse(
                tx.getId(),
                tx.getTenantId(),
                tx.getType(),
                tx.getCategory(),
                tx.getServiceOrderItemId(),
                tx.getSupplierId(),
                supplierName,
                tx.getDescription(),
                tx.getAmount(),
                tx.getPaymentMethod(),
                tx.getStatus(),
                tx.getDueDate(),
                tx.getPaidAt(),
                tx.getCreatedAt()
        );
    }
}
