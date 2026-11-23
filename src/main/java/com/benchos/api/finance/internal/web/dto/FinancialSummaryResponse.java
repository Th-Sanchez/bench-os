package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record FinancialSummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalIncomePaid,
        BigDecimal totalExpensePaid,
        BigDecimal netBalance,
        BigDecimal totalPendingPayables,
        BigDecimal totalOverduePayables,
        Map<TransactionPaymentMethod, BigDecimal> incomeByPaymentMethod,
        Map<TransactionCategory, BigDecimal> expenseByCategory
) {
}
