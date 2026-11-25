package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.internal.entity.FinancialTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemPaymentSummaryResponse(
        TransactionPaymentMethod paymentMethod,
        BigDecimal amount,
        Instant paidAt
) {
    public static ItemPaymentSummaryResponse from(FinancialTransaction tx) {
        return new ItemPaymentSummaryResponse(
                tx.getPaymentMethod(),
                tx.getAmount(),
                tx.getPaidAt() != null ? tx.getPaidAt() : tx.getCreatedAt()
        );
    }
}
