package com.benchos.api.order.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_order_item_quote_options")
public class QuoteOption extends TenantAwareEntity {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Id
    private UUID id;

    @Column(name = "service_order_item_id", nullable = false, updatable = false)
    private UUID serviceOrderItemId;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 300)
    private String description;

    @Column(name = "labor_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal laborCost;

    @Column(name = "parts_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal partsCost;

    @Column(name = "parts_cost_actual", nullable = false, precision = 10, scale = 2)
    private BigDecimal partsCostActual;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "warranty_days", nullable = false)
    private int warrantyDays;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuoteOption() {
    }

    public QuoteOption(
            UUID tenantId,
            UUID serviceOrderItemId,
            int optionIndex,
            String label,
            String description,
            BigDecimal laborCost,
            BigDecimal partsCost,
            BigDecimal partsCostActual,
            BigDecimal discount,
            Integer warrantyDays,
            boolean recommended,
            Instant now
    ) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.serviceOrderItemId = serviceOrderItemId;
        this.optionIndex = optionIndex;
        this.label = requireLabel(label);
        this.description = normalizeOptional(description, 300, "Option description");
        this.laborCost = scaleNonNegative(laborCost, "Option labor cost");
        this.partsCost = scaleNonNegative(partsCost, "Option parts cost");
        this.partsCostActual = partsCostActual != null
                ? scaleNonNegative(partsCostActual, "Option actual parts cost")
                : ZERO;
        this.discount = discount != null
                ? scaleNonNegative(discount, "Option discount")
                : ZERO;
        BigDecimal computedTotal = this.laborCost.add(this.partsCost).subtract(this.discount)
                .setScale(2, RoundingMode.HALF_UP);
        if (computedTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Quote option total price cannot be negative");
        }
        this.totalPrice = computedTotal;
        int resolvedWarranty = warrantyDays != null ? warrantyDays : ServiceOrderItem.DEFAULT_WARRANTY_DAYS;
        if (resolvedWarranty < 0) {
            throw new BusinessRuleException("Option warranty days cannot be negative");
        }
        this.warrantyDays = resolvedWarranty;
        this.recommended = recommended;
        this.createdAt = now != null ? now : Instant.now();
    }

    public BigDecimal getEstimatedNetProfit() {
        return totalPrice.subtract(partsCostActual).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceOrderItemId() {
        return serviceOrderItemId;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public BigDecimal getPartsCost() {
        return partsCost;
    }

    public BigDecimal getPartsCostActual() {
        return partsCostActual;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public int getWarrantyDays() {
        return warrantyDays;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requireLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessRuleException("Quote option label is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 120) {
            throw new BusinessRuleException("Quote option label must be at most 120 characters");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength, String field) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BusinessRuleException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static BigDecimal scaleNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(field + " must be greater than or equal to zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
