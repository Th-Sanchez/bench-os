package com.benchos.api.finance.internal.service;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.finance.internal.entity.FinancialTransaction;
import com.benchos.api.finance.internal.entity.Supplier;
import com.benchos.api.finance.internal.repository.FinancialTransactionRepository;
import com.benchos.api.finance.internal.repository.SupplierRepository;
import com.benchos.api.finance.internal.web.dto.CreateFinancialTransactionRequest;
import com.benchos.api.finance.internal.web.dto.FinancialSummaryResponse;
import com.benchos.api.finance.internal.web.dto.FinancialTransactionResponse;
import com.benchos.api.finance.internal.web.dto.ItemPaymentSummaryResponse;
import com.benchos.api.finance.internal.web.dto.PayTransactionRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import com.benchos.api.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FinanceService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final FinancialTransactionRepository transactionRepository;
    private final SupplierRepository supplierRepository;

    public FinanceService(
            FinancialTransactionRepository transactionRepository,
            SupplierRepository supplierRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public FinancialTransactionResponse createTransaction(CreateFinancialTransactionRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();

        String supplierName = null;
        if (request.supplierId() != null) {
            Supplier supplier = supplierRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.supplierId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
            supplierName = supplier.getName();
        }

        FinancialTransaction tx = FinancialTransaction.createManual(
                tenantId,
                request.type(),
                request.category(),
                request.serviceOrderItemId(),
                request.supplierId(),
                request.description(),
                request.amount(),
                request.paymentMethod(),
                request.status(),
                request.dueDate(),
                Instant.now()
        );

        FinancialTransaction saved = transactionRepository.save(tx);
        return FinancialTransactionResponse.from(saved, supplierName);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> listTransactions(
            TransactionType type,
            TransactionCategory category,
            TransactionStatus status,
            TransactionPaymentMethod paymentMethod,
            UUID supplierId,
            LocalDate from,
            LocalDate to,
            Boolean overdueOnly,
            int page,
            int size
    ) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("Start date cannot be after end date");
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                Sort.by(Sort.Order.desc("createdAt"))
        );

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Specification<FinancialTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplierId"), supplierId));
            }
            if (from != null) {
                Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }
            if (to != null) {
                Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (Boolean.TRUE.equals(overdueOnly)) {
                predicates.add(cb.equal(root.get("status"), TransactionStatus.PENDING));
                predicates.add(cb.isNotNull(root.get("dueDate")));
                predicates.add(cb.lessThan(root.get("dueDate"), today));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<FinancialTransaction> txPage = transactionRepository.findAll(spec, pageable);
        if (txPage.isEmpty()) {
            return PageResponse.of(List.of(), txPage);
        }

        Set<UUID> supplierIds = txPage.getContent().stream()
                .map(FinancialTransaction::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> supplierNames = supplierIds.isEmpty()
                ? Map.of()
                : supplierRepository.findAllByIdInAndTenantId(supplierIds, tenantId)
                        .stream()
                        .collect(Collectors.toMap(Supplier::getId, Supplier::getName));

        List<FinancialTransactionResponse> responses = txPage.getContent().stream()
                .map(tx -> FinancialTransactionResponse.from(
                        tx,
                        tx.getSupplierId() != null ? supplierNames.get(tx.getSupplierId()) : null
                ))
                .toList();

        return PageResponse.of(responses, txPage);
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponse getTransaction(UUID transactionId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        FinancialTransaction tx = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial transaction not found"));

        String supplierName = resolveSupplierName(tx.getSupplierId(), tenantId);
        return FinancialTransactionResponse.from(tx, supplierName);
    }

    @Transactional
    public FinancialTransactionResponse markTransactionAsPaid(UUID transactionId, PayTransactionRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        FinancialTransaction tx = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial transaction not found"));

        TransactionPaymentMethod settlementMethod = request != null ? request.paymentMethod() : null;
        tx.markAsPaid(settlementMethod, Instant.now());

        FinancialTransaction saved = transactionRepository.save(tx);
        String supplierName = resolveSupplierName(saved.getSupplierId(), tenantId);
        return FinancialTransactionResponse.from(saved, supplierName);
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse getSummary(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        LocalDate periodStart = from != null ? from : today.withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (periodStart.isAfter(periodEnd)) {
            throw new BusinessRuleException("Start date cannot be after end date");
        }

        Instant fromInclusive = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<FinancialTransaction> paidInPeriod = transactionRepository.findPaidInPeriod(
                tenantId, fromInclusive, toExclusive
        );

        BigDecimal totalIncomePaid = ZERO;
        BigDecimal totalExpensePaid = ZERO;
        Map<TransactionPaymentMethod, BigDecimal> incomeByMethod = new EnumMap<>(TransactionPaymentMethod.class);
        Map<TransactionCategory, BigDecimal> expenseByCategory = new EnumMap<>(TransactionCategory.class);

        for (FinancialTransaction tx : paidInPeriod) {
            if (tx.getType() == TransactionType.INCOME) {
                totalIncomePaid = totalIncomePaid.add(tx.getAmount());
                incomeByMethod.merge(tx.getPaymentMethod(), tx.getAmount(), BigDecimal::add);
            } else {
                totalExpensePaid = totalExpensePaid.add(tx.getAmount());
                expenseByCategory.merge(tx.getCategory(), tx.getAmount(), BigDecimal::add);
            }
        }

        List<FinancialTransaction> pendingExpenses = transactionRepository.findAllByTenantIdAndTypeAndStatus(
                tenantId, TransactionType.EXPENSE, TransactionStatus.PENDING
        );

        BigDecimal totalPendingPayables = ZERO;
        BigDecimal totalOverduePayables = ZERO;
        for (FinancialTransaction payable : pendingExpenses) {
            totalPendingPayables = totalPendingPayables.add(payable.getAmount());
            if (payable.getDueDate() != null && payable.getDueDate().isBefore(today)) {
                totalOverduePayables = totalOverduePayables.add(payable.getAmount());
            }
        }

        BigDecimal netBalance = totalIncomePaid.subtract(totalExpensePaid);

        return new FinancialSummaryResponse(
                periodStart,
                periodEnd,
                totalIncomePaid,
                totalExpensePaid,
                netBalance,
                totalPendingPayables,
                totalOverduePayables,
                incomeByMethod,
                expenseByCategory
        );
    }

    @Transactional(readOnly = true)
    public List<ItemPaymentSummaryResponse> listRepairPaymentsForItem(UUID serviceOrderItemId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return transactionRepository
                .findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(tenantId, serviceOrderItemId)
                .stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME
                        && tx.getCategory() == TransactionCategory.REPAIR_PAYMENT)
                .map(ItemPaymentSummaryResponse::from)
                .toList();
    }

    private String resolveSupplierName(UUID supplierId, UUID tenantId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findAllByIdInAndTenantId(List.of(supplierId), tenantId)
                .stream()
                .findFirst()
                .map(Supplier::getName)
                .orElse(null);
    }

    private static int clampPageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
