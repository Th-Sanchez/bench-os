package com.benchos.api.order.internal.entity;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.PaymentPart;
import com.benchos.api.order.Voltage;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    @Column(name = "intake_checklist", columnDefinition = "TEXT")
    private String intakeChecklist;

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

    @Column(name = "parts_cost_actual", nullable = false, precision = 10, scale = 2)
    private BigDecimal partsCostActual;

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

    @Column(name = "delivery_signature_svg", columnDefinition = "TEXT")
    private String deliverySignatureSvg;

    @Column(name = "delivery_signer_name", length = 120)
    private String deliverySignerName;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Column(name = "parent_item_id", updatable = false)
    private UUID parentItemId;

    @Column(name = "is_warranty_return", nullable = false, updatable = false)
    private boolean warrantyReturn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

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
        this(
                tenantId,
                serviceOrder,
                itemIndex,
                applianceType,
                brand,
                model,
                voltage,
                serialNumber,
                accessories,
                reportedIssue,
                null,
                null,
                false,
                DEFAULT_WARRANTY_DAYS
        );
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
            String reportedIssue,
            List<IntakeChecklistEntry> intakeChecklist
    ) {
        this(
                tenantId,
                serviceOrder,
                itemIndex,
                applianceType,
                brand,
                model,
                voltage,
                serialNumber,
                accessories,
                reportedIssue,
                intakeChecklist,
                null,
                false,
                DEFAULT_WARRANTY_DAYS
        );
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
            String reportedIssue,
            UUID parentItemId,
            boolean warrantyReturn,
            int initialWarrantyDays
    ) {
        this(
                tenantId,
                serviceOrder,
                itemIndex,
                applianceType,
                brand,
                model,
                voltage,
                serialNumber,
                accessories,
                reportedIssue,
                null,
                parentItemId,
                warrantyReturn,
                initialWarrantyDays
        );
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
            String reportedIssue,
            List<IntakeChecklistEntry> intakeChecklist,
            UUID parentItemId,
            boolean warrantyReturn,
            int initialWarrantyDays
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
        this.intakeChecklist = IntakeChecklistCodec.encodeAndValidate(intakeChecklist);
        this.reportedIssue = reportedIssue.trim();
        this.status = ItemStatus.RECEIVED;
        this.laborCost = ZERO_AMOUNT;
        this.partsCost = ZERO_AMOUNT;
        this.partsCostActual = ZERO_AMOUNT;
        this.discount = ZERO_AMOUNT;
        this.totalPrice = ZERO_AMOUNT;
        this.warrantyDays = Math.max(0, initialWarrantyDays);
        this.parentItemId = parentItemId;
        this.warrantyReturn = warrantyReturn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateQuote(
            String technicalDiagnosis,
            BigDecimal laborCost,
            BigDecimal partsCost,
            BigDecimal discount,
            Integer warrantyDays,
            Instant now
    ) {
        updateQuote(technicalDiagnosis, laborCost, partsCost, null, discount, warrantyDays, now);
    }

    public void updateQuote(
            String technicalDiagnosis,
            BigDecimal laborCost,
            BigDecimal partsCost,
            BigDecimal partsCostActual,
            BigDecimal discount,
            Integer warrantyDays,
            Instant now
    ) {
        if (this.status.isQuoteLockedAfterApproval()) {
            throw new BusinessRuleException(
                    "Quote is locked after approval. Reopen the item to QUOTE_SENT before modifying prices"
            );
        }
        if (!this.status.isQuoteEditable()) {
            throw new BusinessRuleException(
                    "Quote can only be updated while item is in DIAGNOSING or QUOTE_SENT status"
            );
        }

        BigDecimal scaledLabor = scaleMoney(laborCost, "Labor cost");
        BigDecimal scaledParts = scaleMoney(partsCost, "Parts cost");
        BigDecimal scaledPartsActual = partsCostActual != null
                ? scaleMoney(partsCostActual, "Actual parts cost")
                : (this.partsCostActual != null ? this.partsCostActual : ZERO_AMOUNT);
        BigDecimal scaledDiscount = scaleMoney(discount, "Discount");
        BigDecimal computedTotal = scaledLabor.add(scaledParts).subtract(scaledDiscount).setScale(2, RoundingMode.HALF_UP);

        if (computedTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Total price cannot be negative");
        }
        if (warrantyDays != null && warrantyDays < 0) {
            throw new BusinessRuleException("Warranty days cannot be negative");
        }

        this.technicalDiagnosis = normalizeOptional(technicalDiagnosis);
        this.laborCost = scaledLabor;
        this.partsCost = scaledParts;
        this.partsCostActual = scaledPartsActual;
        this.discount = scaledDiscount;
        this.totalPrice = computedTotal;
        if (warrantyDays != null) {
            this.warrantyDays = warrantyDays;
        }
        touch(now);
    }

    public BigDecimal getPartsCostActual() {
        return partsCostActual != null ? partsCostActual : ZERO_AMOUNT;
    }

    public void addActualPartsCost(BigDecimal delta, Instant now) {
        if (delta == null) {
            return;
        }
        BigDecimal scaled = scaleMoney(delta, "Actual parts cost");
        if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        this.partsCostActual = getPartsCostActual().add(scaled).setScale(2, RoundingMode.HALF_UP);
        touch(now != null ? now : Instant.now());
    }

    public BigDecimal getEstimatedNetProfit() {
        BigDecimal revenue = totalPrice != null ? totalPrice : ZERO_AMOUNT;
        return revenue.subtract(getPartsCostActual()).setScale(2, RoundingMode.HALF_UP);
    }

    public void transitionTo(
            ItemStatus targetStatus,
            LocalDate estimatedCompletionDate,
            String cancellationReason,
            Instant now
    ) {
        if (targetStatus == null) {
            throw new BusinessRuleException("Target status is required");
        }
        if (targetStatus == ItemStatus.DELIVERED) {
            throw new BusinessRuleException("Use the delivery endpoint to mark an item as DELIVERED");
        }
        if (targetStatus == ItemStatus.DISCARDED) {
            throw new BusinessRuleException("Use the discard policy transition for DISCARDED status");
        }
        boolean coveredWarrantyFastTrack = this.warrantyReturn
                && this.status == ItemStatus.DIAGNOSING
                && targetStatus == ItemStatus.APPROVED
                && this.totalPrice.compareTo(BigDecimal.ZERO) == 0;
        if (!this.status.canTransitionTo(targetStatus) && !coveredWarrantyFastTrack) {
            throw new BusinessRuleException("Invalid status transition from " + this.status + " to " + targetStatus);
        }

        ItemStatus previousStatus = this.status;

        switch (targetStatus) {
            case APPROVED -> {
                this.approvedAt = now;
                this.estimatedCompletionDate = estimatedCompletionDate;
            }
            case QUOTE_SENT -> {
                if (previousStatus.isQuoteLockedAfterApproval()) {
                    this.approvedAt = null;
                    this.estimatedCompletionDate = null;
                }
            }
            case READY_FOR_PICKUP -> this.finishedAt = now;
            case CANCELLED -> {
                String normalizedReason = normalizeOptional(cancellationReason);
                if (normalizedReason == null) {
                    throw new BusinessRuleException("Cancellation reason is required");
                }
                this.cancellationReason = normalizedReason;
            }
            default -> {
                // No additional side effects for DIAGNOSING, WAITING_PARTS, IN_REPAIR, REJECTED
            }
        }

        this.status = targetStatus;
        touch(now);
    }

    public void markDiscarded(int abandonmentDays, Instant now) {
        if (!this.status.canTransitionTo(ItemStatus.DISCARDED)) {
            throw new BusinessRuleException("Only items in READY_FOR_PICKUP or REJECTED status can be discarded");
        }
        Instant referenceTimestamp = (this.status == ItemStatus.READY_FOR_PICKUP && this.finishedAt != null)
                ? this.finishedAt
                : this.updatedAt;
        Instant eligibleAt = referenceTimestamp.plus(Duration.ofDays(abandonmentDays));
        if (now.isBefore(eligibleAt)) {
            throw new BusinessRuleException(
                    "Item cannot be discarded before " + abandonmentDays + " days of abandonment"
            );
        }

        this.status = ItemStatus.DISCARDED;
        this.discardedAt = now;
        touch(now);
    }

    public Optional<OrderItemDeliveredEvent> deliver(List<PaymentPart> payments, Instant now) {
        return deliver(payments, null, null, now);
    }

    public Optional<OrderItemDeliveredEvent> deliver(
            List<PaymentPart> payments,
            String deliverySignatureSvg,
            String deliverySignerName,
            Instant now
    ) {
        List<PaymentPart> normalizedPayments = payments == null ? List.of() : payments;
        String sanitizedSignature = DeliverySignatureSanitizer.sanitize(deliverySignatureSvg);
        String normalizedSignerName = normalizeOptional(deliverySignerName);
        if (normalizedSignerName != null && normalizedSignerName.length() > 120) {
            throw new BusinessRuleException("Delivery signer name must be at most 120 characters");
        }

        if (this.status == ItemStatus.READY_FOR_PICKUP) {
            if (this.totalPrice.compareTo(BigDecimal.ZERO) > 0) {
                if (normalizedPayments.isEmpty()) {
                    throw new BusinessRuleException("At least one payment part is required to deliver a repaired item");
                }

                BigDecimal sum = ZERO_AMOUNT;
                List<PaymentPart> scaledParts = new java.util.ArrayList<>(normalizedPayments.size());
                for (PaymentPart part : normalizedPayments) {
                    if (part == null || part.paymentMethod() == null || part.amount() == null) {
                        throw new BusinessRuleException("Each payment part must specify a payment method and amount");
                    }
                    BigDecimal scaledAmount = part.amount().setScale(2, RoundingMode.HALF_UP);
                    if (scaledAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessRuleException("Payment part amount must be greater than zero");
                    }
                    sum = sum.add(scaledAmount);
                    scaledParts.add(new PaymentPart(part.paymentMethod(), scaledAmount));
                }

                if (sum.compareTo(this.totalPrice) != 0) {
                    throw new BusinessRuleException(
                            "Sum of payments (" + sum + ") must equal item total price (" + this.totalPrice + ")"
                    );
                }

                this.status = ItemStatus.DELIVERED;
                this.deliveredAt = now;
                this.deliverySignatureSvg = sanitizedSignature;
                this.deliverySignerName = normalizedSignerName;
                touch(now);

                return Optional.of(new OrderItemDeliveredEvent(
                        getTenantId(),
                        this.serviceOrder.getId(),
                        this.id,
                        this.serviceOrder.getOrderNumber(),
                        this.itemIndex,
                        this.totalPrice,
                        scaledParts,
                        now
                ));
            }

            if (!normalizedPayments.isEmpty()) {
                throw new BusinessRuleException("Payments must not be provided when item total price is zero");
            }
            this.status = ItemStatus.DELIVERED;
            this.deliveredAt = now;
            this.deliverySignatureSvg = sanitizedSignature;
            this.deliverySignerName = normalizedSignerName;
            touch(now);
            return Optional.empty();
        }

        if (this.status == ItemStatus.REJECTED) {
            if (!normalizedPayments.isEmpty()) {
                throw new BusinessRuleException("Rejected items are delivered without charge and must not include payments");
            }
            this.laborCost = ZERO_AMOUNT;
            this.partsCost = ZERO_AMOUNT;
            this.discount = ZERO_AMOUNT;
            this.totalPrice = ZERO_AMOUNT;
            this.status = ItemStatus.DELIVERED;
            this.deliveredAt = now;
            this.deliverySignatureSvg = sanitizedSignature;
            this.deliverySignerName = normalizedSignerName;
            touch(now);
            return Optional.empty();
        }

        throw new BusinessRuleException("Only items in READY_FOR_PICKUP or REJECTED status can be delivered");
    }

    public String getTag() {
        return serviceOrder.getOrderNumber() + "-" + itemIndex;
    }

    public String getApplianceDisplayName() {
        StringBuilder sb = new StringBuilder(applianceType).append(' ').append(brand);
        if (model != null && !model.isBlank()) {
            sb.append(' ').append(model);
        }
        return sb.toString();
    }

    private void touch(Instant now) {
        this.updatedAt = now;
        this.serviceOrder.touch();
    }

    private static BigDecimal scaleMoney(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(fieldName + " must be greater than or equal to zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
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

    public List<IntakeChecklistEntry> getIntakeChecklist() {
        return IntakeChecklistCodec.decode(intakeChecklist);
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

    public String getDeliverySignatureSvg() {
        return deliverySignatureSvg;
    }

    public String getDeliverySignerName() {
        return deliverySignerName;
    }

    public int remainingWarrantyDays(Instant now) {
        if (this.status != ItemStatus.DELIVERED
                || this.deliveredAt == null
                || this.warrantyDays <= 0
                || this.totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        Instant expiresAt = this.deliveredAt.plus(Duration.ofDays(this.warrantyDays));
        if (!expiresAt.isAfter(now)) {
            return 0;
        }
        long remainingSeconds = Duration.between(now, expiresAt).getSeconds();
        long secondsPerDay = Duration.ofDays(1).getSeconds();
        return (int) Math.max(0L, (remainingSeconds + secondsPerDay - 1L) / secondsPerDay);
    }

    public UUID getParentItemId() {
        return parentItemId;
    }

    public boolean isWarrantyReturn() {
        return warrantyReturn;
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

    public long getVersion() {
        return version;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
