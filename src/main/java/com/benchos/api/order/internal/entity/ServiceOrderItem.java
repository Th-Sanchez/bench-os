package com.benchos.api.order.internal.entity;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "service_order_items")
public class ServiceOrderItem extends TenantAwareEntity {

    public static final int DEFAULT_WARRANTY_DAYS = 90;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_id", nullable = false, updatable = false)
    private ServiceOrder serviceOrder;

    @Column(name = "item_index", nullable = false, updatable = false)
    private int itemIndex;

    @Column(name = "appliance_type", nullable = false, length = 80)
    private String applianceType;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Voltage voltage;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(columnDefinition = "TEXT")
    private String accessories;

    @Column(name = "reported_issue", nullable = false, columnDefinition = "TEXT")
    private String reportedIssue;

    @Column(name = "technical_diagnosis", columnDefinition = "TEXT")
    private String technicalDiagnosis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ItemStatus status;

    @Column(name = "labor_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal laborCost;

    @Column(name = "parts_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal partsCost;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "estimated_completion_date")
    private LocalDate estimatedCompletionDate;

    @Column(name = "warranty_days", nullable = false)
    private int warrantyDays;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceOrderItem() {
    }

    ServiceOrderItem(
            UUID tenantId,
            ServiceOrder serviceOrder,
            int itemIndex,
            String applianceType,
            String brand,
            String model,
            Voltage voltage,
            String serialNumber,
            String accessories,
            String reportedIssue
    ) {
        super(tenantId);
        Instant now = Instant.now();
        this.id = UuidV7Generator.next();
        this.serviceOrder = serviceOrder;
        this.itemIndex = itemIndex;
        this.applianceType = applianceType.trim();
        this.brand = brand.trim();
        this.model = normalizeOptional(model);
        this.voltage = voltage;
        this.serialNumber = normalizeOptional(serialNumber);
        this.accessories = normalizeOptional(accessories);
        this.reportedIssue = reportedIssue.trim();
        this.status = ItemStatus.RECEIVED;
        this.laborCost = ZERO_AMOUNT;
        this.partsCost = ZERO_AMOUNT;
        this.discount = ZERO_AMOUNT;
        this.totalPrice = ZERO_AMOUNT;
        this.warrantyDays = DEFAULT_WARRANTY_DAYS;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getTag() {
        return serviceOrder.getOrderNumber() + "-" + itemIndex;
    }

    public UUID getId() {
        return id;
    }

    public ServiceOrder getServiceOrder() {
        return serviceOrder;
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public String getApplianceType() {
        return applianceType;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Voltage getVoltage() {
        return voltage;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getAccessories() {
        return accessories;
    }

    public String getReportedIssue() {
        return reportedIssue;
    }

    public String getTechnicalDiagnosis() {
        return technicalDiagnosis;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public BigDecimal getPartsCost() {
        return partsCost;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public LocalDate getEstimatedCompletionDate() {
        return estimatedCompletionDate;
    }

    public int getWarrantyDays() {
        return warrantyDays;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getDiscardedAt() {
        return discardedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
