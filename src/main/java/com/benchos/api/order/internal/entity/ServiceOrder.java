package com.benchos.api.order.internal.entity;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_orders")
public class ServiceOrder extends TenantAwareEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "order_number", nullable = false, updatable = false)
    private long orderNumber;

    @Column(name = "public_token", nullable = false, unique = true, updatable = false)
    private UUID publicToken;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "customer_rating")
    private Integer customerRating;

    @Column(name = "customer_feedback", columnDefinition = "TEXT")
    private String customerFeedback;

    @Column(name = "customer_rated_at")
    private Instant customerRatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "serviceOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemIndex ASC")
    private List<ServiceOrderItem> items = new ArrayList<>();

    protected ServiceOrder() {
    }

    public ServiceOrder(UUID tenantId, UUID customerId, UUID createdByUserId, long orderNumber, String notes) {
        super(tenantId);
        Instant now = Instant.now();
        this.id = UuidV7Generator.next();
        this.customerId = customerId;
        this.createdByUserId = createdByUserId;
        this.orderNumber = orderNumber;
        this.publicToken = UUID.randomUUID();
        this.notes = normalizeOptional(notes);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public ServiceOrderItem addItem(
            String applianceType,
            String brand,
            String model,
            Voltage voltage,
            String serialNumber,
            String accessories,
            String reportedIssue
    ) {
        int nextIndex = this.items.size() + 1;
        ServiceOrderItem item = new ServiceOrderItem(
                getTenantId(),
                this,
                nextIndex,
                applianceType,
                brand,
                model,
                voltage,
                serialNumber,
                accessories,
                reportedIssue
        );
        this.items.add(item);
        touch();
        return item;
    }

    public ServiceOrderItem addWarrantyReturnItem(
            ServiceOrderItem parentItem,
            String reportedIssue,
            String accessoriesOverride,
            int carriedWarrantyDays
    ) {
        int nextIndex = this.items.size() + 1;
        String resolvedAccessories = (accessoriesOverride != null && !accessoriesOverride.isBlank())
                ? accessoriesOverride
                : parentItem.getAccessories();
        ServiceOrderItem item = new ServiceOrderItem(
                getTenantId(),
                this,
                nextIndex,
                parentItem.getApplianceType(),
                parentItem.getBrand(),
                parentItem.getModel(),
                parentItem.getVoltage(),
                parentItem.getSerialNumber(),
                resolvedAccessories,
                reportedIssue,
                parentItem.getId(),
                true,
                carriedWarrantyDays
        );
        this.items.add(item);
        touch();
        return item;
    }

    public void submitCustomerRating(int rating, String feedback, Instant now) {
        if (rating < 1 || rating > 5) {
            throw new BusinessRuleException("Customer rating must be between 1 and 5");
        }
        if (this.customerRating != null) {
            throw new BusinessRuleException("This service order has already been rated");
        }
        boolean hasDeliveredItem = this.items.stream()
                .anyMatch(item -> item.getStatus() == ItemStatus.DELIVERED);
        if (!hasDeliveredItem) {
            throw new BusinessRuleException("Service order can only be rated after at least one item is delivered");
        }
        String normalizedFeedback = normalizeOptional(feedback);
        if (normalizedFeedback != null && normalizedFeedback.length() > 1000) {
            throw new BusinessRuleException("Customer feedback must be at most 1000 characters");
        }

        this.customerRating = rating;
        this.customerFeedback = normalizedFeedback;
        this.customerRatedAt = now;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public long getOrderNumber() {
        return orderNumber;
    }

    public UUID getPublicToken() {
        return publicToken;
    }

    public String getNotes() {
        return notes;
    }

    public Integer getCustomerRating() {
        return customerRating;
    }

    public String getCustomerFeedback() {
        return customerFeedback;
    }

    public Instant getCustomerRatedAt() {
        return customerRatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ServiceOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
