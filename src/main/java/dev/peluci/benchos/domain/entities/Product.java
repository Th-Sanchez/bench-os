package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Product extends BaseEntity {

    private UUID tenantId;
    private String name;
    private String category;
    private BigDecimal price;

    public Product(UUID tenantId, String name, String category, BigDecimal price) {
        super();
        this.tenantId = tenantId;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    public Product(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                   UUID tenantId, String name, String category, BigDecimal price) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
