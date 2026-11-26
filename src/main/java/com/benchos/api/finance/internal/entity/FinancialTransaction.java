package com.benchos.api.finance.internal.entity;

import com.benchos.api.finance.TransactionCategory;
import com.benchos.api.finance.TransactionPaymentMethod;
import com.benchos.api.finance.TransactionStatus;
import com.benchos.api.finance.TransactionType;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction extends TenantAwareEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TransactionCategory category;

    @Column(name = "service_order_item_id")
    private UUID serviceOrderItemId;

    @Column(name = "part_request_id")
    private UUID partRequestId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private TransactionPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FinancialTransaction() {
    }

    private FinancialTransaction(
            UUID tenantId,
            TransactionType type,
            TransactionCategory category,
            UUID serviceOrderItemId,
            UUID partRequestId,
            UUID supplierId,
            String description,
            BigDecimal amount,
            TransactionPaymentMethod paymentMethod,
            TransactionStatus status,
            LocalDate dueDate,
            Instant paidAt,
            Instant createdAt
    ) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.type = type;
        this.category = category;
        this.serviceOrderItemId = serviceOrderItemId;
        this.partRequestId = partRequestId;
        this.supplierId = supplierId;
        this.description = description.trim();
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.dueDate = dueDate;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public static FinancialTransaction repairIncome(
            UUID tenantId,
            UUID serviceOrderItemId,
            long orderNumber,
            int itemIndex,
            BigDecimal amount,
            TransactionPaymentMethod paymentMethod,
            Instant paidAt
    ) {
        if (paymentMethod == TransactionPaymentMethod.STORE_CREDIT) {
            throw new BusinessRuleException("Repair payments cannot use STORE_CREDIT");
        }
        BigDecimal scaledAmount = requirePositiveAmount(amount);
        Instant effectivePaidAt = paidAt != null ? paidAt : Instant.now();
        String description = "Repair payment - Order #" + orderNumber + "-" + itemIndex + " (" + paymentMethod + ")";
        return new FinancialTransaction(
                tenantId,
                TransactionType.INCOME,
                TransactionCategory.REPAIR_PAYMENT,
                serviceOrderItemId,
                null,
                null,
                description,
                scaledAmount,
                paymentMethod,
                TransactionStatus.PAID,
                null,
                effectivePaidAt,
                Instant.now()
        );
    }

    public static FinancialTransaction partsPurchaseExpense(
            UUID tenantId,
            UUID serviceOrderItemId,
            UUID partRequestId,
            long orderNumber,
            int itemIndex,
            String partName,
            int quantity,
            String supplierName,
            BigDecimal amount,
            TransactionPaymentMethod paymentMethod,
            Instant paidAt
    ) {
        if (paymentMethod == TransactionPaymentMethod.STORE_CREDIT) {
            throw new BusinessRuleException("Automatic part purchase expenses cannot use STORE_CREDIT");
        }
        BigDecimal scaledAmount = requirePositiveAmount(amount);
        Instant effectivePaidAt = paidAt != null ? paidAt : Instant.now();
        String qtyPrefix = quantity > 1 ? quantity + "x " : "";
        String supplierSuffix = (supplierName != null && !supplierName.isBlank())
                ? " (" + supplierName.trim() + ")"
                : "";
        String rawDescription = "Peça " + qtyPrefix + partName.trim()
                + " - OS #" + orderNumber + "-" + itemIndex + supplierSuffix;
        String description = rawDescription.length() > 255
                ? rawDescription.substring(0, 255)
                : rawDescription;
        return new FinancialTransaction(
                tenantId,
                TransactionType.EXPENSE,
                TransactionCategory.PARTS_PURCHASE,
                serviceOrderItemId,
                partRequestId,
                null,
                description,
                scaledAmount,
                paymentMethod,
                TransactionStatus.PAID,
                null,
                effectivePaidAt,
                Instant.now()
        );
    }

    public static FinancialTransaction createManual(
            UUID tenantId,
            TransactionType type,
            TransactionCategory category,
            UUID serviceOrderItemId,
            UUID supplierId,
            String description,
            BigDecimal amount,
            TransactionPaymentMethod paymentMethod,
            TransactionStatus requestedStatus,
            LocalDate dueDate,
            Instant now
    ) {
        BigDecimal scaledAmount = requirePositiveAmount(amount);
        TransactionStatus resolvedStatus;

        if (paymentMethod == TransactionPaymentMethod.STORE_CREDIT) {
            if (type != TransactionType.EXPENSE) {
                throw new BusinessRuleException("STORE_CREDIT payment method is only allowed for expenses");
            }
            if (supplierId == null) {
                throw new BusinessRuleException("Supplier is required for STORE_CREDIT transactions");
            }
            if (dueDate == null) {
                throw new BusinessRuleException("Due date is required for STORE_CREDIT transactions");
            }
            resolvedStatus = requestedStatus != null ? requestedStatus : TransactionStatus.PENDING;
        } else {
            resolvedStatus = requestedStatus != null ? requestedStatus : TransactionStatus.PAID;
        }

        Instant paidAt = resolvedStatus == TransactionStatus.PAID ? now : null;

        return new FinancialTransaction(
                tenantId,
                type,
                category,
                serviceOrderItemId,
                null,
                supplierId,
                description,
                scaledAmount,
                paymentMethod,
                resolvedStatus,
                dueDate,
                paidAt,
                now
        );
    }

    public void markAsPaid(TransactionPaymentMethod settlementPaymentMethod, Instant now) {
        if (this.status == TransactionStatus.PAID) {
            throw new BusinessRuleException("Transaction is already paid");
        }
        if (settlementPaymentMethod != null) {
            if (settlementPaymentMethod == TransactionPaymentMethod.STORE_CREDIT) {
                throw new BusinessRuleException("Cannot settle a pending transaction using STORE_CREDIT");
            }
            this.paymentMethod = settlementPaymentMethod;
        } else if (this.paymentMethod == TransactionPaymentMethod.STORE_CREDIT) {
            throw new BusinessRuleException("A settlement payment method is required to pay a STORE_CREDIT balance");
        }

        this.status = TransactionStatus.PAID;
        this.paidAt = now;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessRuleException("Transaction amount is required");
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Transaction amount must be greater than zero");
        }
        return scaled;
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public UUID getServiceOrderItemId() {
        return serviceOrderItemId;
    }

    public UUID getPartRequestId() {
        return partRequestId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
