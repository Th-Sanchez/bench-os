package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.BudgetItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BudgetItem extends BaseEntity {

    private UUID tenantId;
    private UUID serviceOrderId;
    private String description;
    private BudgetItemType type;
    private BigDecimal unitPrice;
    private int quantity;

    public BudgetItem(UUID tenantId, UUID serviceOrderId, String description, BudgetItemType type, BigDecimal unitPrice, int quantity) {
        super();
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.description = description;
        this.type = type;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public BudgetItem(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                      UUID tenantId, UUID serviceOrderId, String description, BudgetItemType type, BigDecimal unitPrice, int quantity) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.description = description;
        this.type = type;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getServiceOrderId() {
        return serviceOrderId;
    }

    public String getDescription() {
        return description;
    }

    public BudgetItemType getType() {
        return type;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
