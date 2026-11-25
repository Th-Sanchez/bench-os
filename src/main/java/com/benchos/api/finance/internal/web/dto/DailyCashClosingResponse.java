package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyCashClosingResponse(
        LocalDate date,
        String zone,
        BigDecimal totalIncomePaid,
        BigDecimal totalExpensePaid,
        BigDecimal netCashFlow,
        int incomeCount,
        int expenseCount,
        Map<TransactionPaymentMethod, BigDecimal> incomeByPaymentMethod,
        Map<TransactionPaymentMethod, BigDecimal> expenseByPaymentMethod,
        List<FinancialTransactionResponse> transactions
) {
}
