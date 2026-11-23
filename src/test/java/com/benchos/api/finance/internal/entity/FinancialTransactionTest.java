package com.benchos.api.finance.internal.entity;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialTransactionTest {

    private static final Instant NOW = Instant.parse("2026-05-10T15:00:00Z");

    @Test
    void enforcesStoreCreditInvariantsAndSettlementRules() {
        UUID tenantId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        LocalDate dueDate = LocalDate.of(2026, 5, 25);

        // STORE_CREDIT requires EXPENSE, supplier, and dueDate
        assertThatThrownBy(() -> FinancialTransaction.createManual(
                tenantId, TransactionType.INCOME, TransactionCategory.OTHER, null, supplierId,
                "Invalid income credit", new BigDecimal("50.00"), TransactionPaymentMethod.STORE_CREDIT,
                null, dueDate, NOW
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> FinancialTransaction.createManual(
                tenantId, TransactionType.EXPENSE, TransactionCategory.PARTS_PURCHASE, null, null,
                "Missing supplier", new BigDecimal("50.00"), TransactionPaymentMethod.STORE_CREDIT,
                null, dueDate, NOW
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> FinancialTransaction.createManual(
                tenantId, TransactionType.EXPENSE, TransactionCategory.PARTS_PURCHASE, null, supplierId,
                "Missing due date", new BigDecimal("50.00"), TransactionPaymentMethod.STORE_CREDIT,
                null, null, NOW
        )).isInstanceOf(BusinessRuleException.class);

        // Valid STORE_CREDIT defaults to PENDING with null paidAt
        FinancialTransaction tx = FinancialTransaction.createManual(
                tenantId, TransactionType.EXPENSE, TransactionCategory.PARTS_PURCHASE, null, supplierId,
                "Magnetrons batch", new BigDecimal("320.00"), TransactionPaymentMethod.STORE_CREDIT,
                null, dueDate, NOW
        );
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getPaidAt()).isNull();
        assertThat(tx.getDueDate()).isEqualTo(dueDate);

        // Cannot settle STORE_CREDIT without a real settlement payment method
        assertThatThrownBy(() -> tx.markAsPaid(null, NOW.plusSeconds(60)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> tx.markAsPaid(TransactionPaymentMethod.STORE_CREDIT, NOW.plusSeconds(60)))
                .isInstanceOf(BusinessRuleException.class);

        // Settle via PIX -> PAID
        Instant paidTime = NOW.plusSeconds(120);
        tx.markAsPaid(TransactionPaymentMethod.PIX, paidTime);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PAID);
        assertThat(tx.getPaymentMethod()).isEqualTo(TransactionPaymentMethod.PIX);
        assertThat(tx.getPaidAt()).isEqualTo(paidTime);

        // Double pay rejected
        assertThatThrownBy(() -> tx.markAsPaid(TransactionPaymentMethod.CASH, paidTime.plusSeconds(10)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createsRepairIncomeAsPaidAndRejectsNonPositiveAmounts() {
        UUID tenantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        FinancialTransaction income = FinancialTransaction.repairIncome(
                tenantId, itemId, 1042L, 1, new BigDecimal("180.50"), TransactionPaymentMethod.PIX, NOW
        );
        assertThat(income.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(income.getCategory()).isEqualTo(TransactionCategory.REPAIR_PAYMENT);
        assertThat(income.getStatus()).isEqualTo(TransactionStatus.PAID);
        assertThat(income.getAmount()).isEqualByComparingTo("180.50");
        assertThat(income.getDescription()).contains("1042-1");

        assertThatThrownBy(() -> FinancialTransaction.repairIncome(
                tenantId, itemId, 1042L, 1, BigDecimal.ZERO, TransactionPaymentMethod.PIX, NOW
        )).isInstanceOf(BusinessRuleException.class);
    }
}
