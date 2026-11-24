package com.benchos.api.finance.internal.repository;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.finance.internal.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, UUID>, JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndServiceOrderItemIdAndTypeAndCategory(
            UUID tenantId,
            UUID serviceOrderItemId,
            TransactionType type,
            TransactionCategory category
    );

    List<FinancialTransaction> findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(
            UUID tenantId,
            UUID serviceOrderItemId
    );

    @Query("""
            SELECT t FROM FinancialTransaction t
            WHERE t.tenantId = :tenantId
              AND t.status = com.benchos.api.finance.TransactionStatus.PAID
              AND COALESCE(t.paidAt, t.createdAt) >= :fromInclusive
              AND COALESCE(t.paidAt, t.createdAt) < :toExclusive
            """)
    List<FinancialTransaction> findPaidInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    List<FinancialTransaction> findAllByTenantIdAndTypeAndStatus(
            UUID tenantId,
            TransactionType type,
            TransactionStatus status
    );

    @Query("""
            SELECT t FROM FinancialTransaction t
            WHERE t.type = com.benchos.api.finance.TransactionType.EXPENSE
              AND t.status = com.benchos.api.finance.TransactionStatus.PENDING
              AND t.dueDate IS NOT NULL
              AND t.dueDate <= :cutoffDate
            ORDER BY t.dueDate ASC
            """)
    List<FinancialTransaction> findAllPendingExpensesDueOnOrBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
