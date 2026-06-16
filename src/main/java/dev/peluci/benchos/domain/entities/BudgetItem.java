package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.BudgetItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BudgetItem extends BaseEntity {

    private ServiceOrder serviceOrder;
    private String description;
    private BudgetItemType type;
    private BigDecimal unitPrice;
    private Integer quantity;

    public BudgetItem(ServiceOrder serviceOrder, String description, BudgetItemType type, BigDecimal unitPrice, Integer quantity) {
        super();
        this.serviceOrder = serviceOrder;
        this.description = description;
        this.type = type;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public BudgetItem(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                      ServiceOrder serviceOrder, String description, BudgetItemType type, BigDecimal unitPrice, Integer quantity) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.serviceOrder = serviceOrder;
        this.description = description;
        this.type = type;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public ServiceOrder getServiceOrder() {
        return serviceOrder;
    }

    public void setServiceOrder(ServiceOrder serviceOrder) {
        this.serviceOrder = serviceOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BudgetItemType getType() {
        return type;
    }

    public void setType(BudgetItemType type) {
        this.type = type;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
