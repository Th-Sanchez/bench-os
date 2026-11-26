package com.benchos.api.order.internal.entity;

import com.benchos.api.order.PartRequestStatus;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "part_requests")
public class PartRequest extends TenantAwareEntity {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_item_id", nullable = false, updatable = false)
    private ServiceOrderItem serviceOrderItem;

    @Column(name = "part_catalog_id")
    private UUID partCatalogId;

    @Column(name = "part_name", nullable = false, length = 150)
    private String partName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "estimated_unit_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedUnitCost;

    @Column(name = "actual_unit_cost", precision = 10, scale = 2)
    private BigDecimal actualUnitCost;

    @Column(name = "supplier_name", length = 120)
    private String supplierName;

    @Column(name = "purchase_url", length = 500)
    private String purchaseUrl;

    @Column(length = 400)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartRequestStatus status;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "expense_recorded", nullable = false)
    private boolean expenseRecorded;

    @Column(name = "created_by_user_id", updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PartRequest() {
    }

    public PartRequest(
            UUID tenantId,
            ServiceOrderItem serviceOrderItem,
            UUID partCatalogId,
            String partName,
            int quantity,
            BigDecimal estimatedUnitCost,
            String supplierName,
            String purchaseUrl,
            String notes,
            UUID createdByUserId,
            Instant now
    ) {
        super(tenantId);
        Instant timestamp = now != null ? now : Instant.now();
        this.id = UuidV7Generator.next();
        this.serviceOrderItem = serviceOrderItem;
        this.partCatalogId = partCatalogId;
        this.partName = requirePartName(partName);
        this.quantity = requireQuantity(quantity);
        this.estimatedUnitCost = scaleNonNegative(estimatedUnitCost != null ? estimatedUnitCost : ZERO, "Estimated unit cost");
        this.actualUnitCost = null;
        this.supplierName = normalizeOptional(supplierName, 120, "Supplier name");
        this.purchaseUrl = validatePurchaseUrl(purchaseUrl);
        this.notes = normalizeOptional(notes, 400, "Notes");
        this.status = PartRequestStatus.TO_ORDER;
        this.orderedAt = null;
        this.arrivedAt = null;
        this.expenseRecorded = false;
        this.createdByUserId = createdByUserId;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void updateDetails(
            String partName,
            int quantity,
            BigDecimal estimatedUnitCost,
            BigDecimal actualUnitCost,
            String supplierName,
            String purchaseUrl,
            String notes,
            Instant now
    ) {
        if (!this.status.isPending()) {
            throw new BusinessRuleException("Only pending part requests (TO_ORDER or ORDERED) can be edited");
        }
        this.partName = requirePartName(partName);
        this.quantity = requireQuantity(quantity);
        this.estimatedUnitCost = scaleNonNegative(estimatedUnitCost != null ? estimatedUnitCost : ZERO, "Estimated unit cost");
        this.actualUnitCost = actualUnitCost != null
                ? scaleNonNegative(actualUnitCost, "Actual unit cost")
                : null;
        this.supplierName = normalizeOptional(supplierName, 120, "Supplier name");
        this.purchaseUrl = validatePurchaseUrl(purchaseUrl);
        this.notes = normalizeOptional(notes, 400, "Notes");
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void markOrdered(
            String supplierName,
            String purchaseUrl,
            BigDecimal actualUnitCost,
            String notes,
            Instant now
    ) {
        if (this.status != PartRequestStatus.TO_ORDER) {
            throw new BusinessRuleException("Only part requests in TO_ORDER status can be marked as ordered");
        }
        Instant timestamp = now != null ? now : Instant.now();
        if (supplierName != null) {
            this.supplierName = normalizeOptional(supplierName, 120, "Supplier name");
        }
        if (purchaseUrl != null) {
            this.purchaseUrl = validatePurchaseUrl(purchaseUrl);
        }
        if (actualUnitCost != null) {
            this.actualUnitCost = scaleNonNegative(actualUnitCost, "Actual unit cost");
        }
        if (notes != null) {
            this.notes = normalizeOptional(notes, 400, "Notes");
        }
        this.status = PartRequestStatus.ORDERED;
        this.orderedAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void reopenToOrder(Instant now) {
        if (this.status != PartRequestStatus.ORDERED) {
            throw new BusinessRuleException("Only ORDERED part requests can be reopened to TO_ORDER");
        }
        this.status = PartRequestStatus.TO_ORDER;
        this.orderedAt = null;
        this.updatedAt = now != null ? now : Instant.now();
    }

    public void markArrived(BigDecimal actualUnitCost, boolean recordExpense, Instant now) {
        if (!this.status.isPending()) {
            throw new BusinessRuleException("Only pending part requests (TO_ORDER or ORDERED) can be marked as arrived");
        }
        Instant timestamp = now != null ? now : Instant.now();
        if (actualUnitCost != null) {
            this.actualUnitCost = scaleNonNegative(actualUnitCost, "Actual unit cost");
        } else if (this.actualUnitCost == null) {
            this.actualUnitCost = this.estimatedUnitCost;
        }
        this.status = PartRequestStatus.ARRIVED;
        this.arrivedAt = timestamp;
        this.expenseRecorded = recordExpense && getEffectiveTotalCost().compareTo(BigDecimal.ZERO) > 0;
        this.updatedAt = timestamp;
    }

    public BigDecimal getEstimatedTotalCost() {
        return estimatedUnitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getEffectiveUnitCost() {
        return actualUnitCost != null ? actualUnitCost : estimatedUnitCost;
    }

    public BigDecimal getEffectiveTotalCost() {
        return getEffectiveUnitCost().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public ServiceOrderItem getServiceOrderItem() {
        return serviceOrderItem;
    }

    public UUID getPartCatalogId() {
        return partCatalogId;
    }

    public String getPartName() {
        return partName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getEstimatedUnitCost() {
        return estimatedUnitCost;
    }

    public BigDecimal getActualUnitCost() {
        return actualUnitCost;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getPurchaseUrl() {
        return purchaseUrl;
    }

    public String getNotes() {
        return notes;
    }

    public PartRequestStatus getStatus() {
        return status;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public boolean isExpenseRecorded() {
        return expenseRecorded;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requirePartName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessRuleException("Part name is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 150) {
            throw new BusinessRuleException("Part name must be at most 150 characters");
        }
        return trimmed;
    }

    private static int requireQuantity(int quantity) {
        if (quantity < 1 || quantity > 999) {
            throw new BusinessRuleException("Quantity must be between 1 and 999");
        }
        return quantity;
    }

    private static BigDecimal scaleNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BusinessRuleException(fieldName + " is required");
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(fieldName + " cannot be negative");
        }
        return scaled;
    }

    private static String normalizeOptional(String raw, int maxLength, String fieldName) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BusinessRuleException(fieldName + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String validatePurchaseUrl(String raw) {
        String normalized = normalizeOptional(raw, 500, "Purchase URL");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessRuleException("Purchase URL must be a valid http or https link");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Purchase URL must be a valid http or https link");
        }
    }
}
