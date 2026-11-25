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
@Table(name = "parts_catalog")
public class PartCatalogItem extends TenantAwareEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "appliance_category", length = 255)
    private String applianceCategory;

    @Column(name = "average_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal averageCost;

    @Column(name = "suggested_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal suggestedPrice;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PartCatalogItem() {
    }

    public PartCatalogItem(
            UUID tenantId,
            String name,
            String applianceCategory,
            BigDecimal averageCost,
            BigDecimal suggestedPrice,
            String notes
    ) {
        super(tenantId);
        Instant now = Instant.now();
        this.id = UuidV7Generator.next();
        this.name = requireName(name);
        this.applianceCategory = normalizeOptional(applianceCategory);
        this.averageCost = scaleMoney(averageCost, "Average cost");
        this.suggestedPrice = scaleMoney(suggestedPrice, "Suggested price");
        this.notes = normalizeOptional(notes);
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String applianceCategory,
            BigDecimal averageCost,
            BigDecimal suggestedPrice,
            String notes
    ) {
        this.name = requireName(name);
        this.applianceCategory = normalizeOptional(applianceCategory);
        this.averageCost = scaleMoney(averageCost, "Average cost");
        this.suggestedPrice = scaleMoney(suggestedPrice, "Suggested price");
        this.notes = normalizeOptional(notes);
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getApplianceCategory() {
        return applianceCategory;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getSuggestedPrice() {
        return suggestedPrice;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessRuleException("Part name is required");
        }
        return raw.trim();
    }

    private static String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal scaleMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BusinessRuleException(fieldName + " is required");
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(fieldName + " cannot be negative");
        }
        return scaled;
    }
}
