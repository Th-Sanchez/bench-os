package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Sale extends BaseEntity {

    private UUID tenantId;
    private UUID productId;
    private UUID customerId;
    private int quantity;
    private BigDecimal totalPrice;
    private LocalDateTime soldAt;

    public Sale(UUID tenantId, UUID productId, UUID customerId, int quantity, BigDecimal totalPrice, LocalDateTime soldAt) {
        super();
        this.tenantId = tenantId;
        this.productId = productId;
        this.customerId = customerId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.soldAt = soldAt;
    }

    public Sale(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                UUID tenantId, UUID productId, UUID customerId, int quantity, BigDecimal totalPrice, LocalDateTime soldAt) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.productId = productId;
        this.customerId = customerId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.soldAt = soldAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public LocalDateTime getSoldAt() {
        return soldAt;
    }
}
