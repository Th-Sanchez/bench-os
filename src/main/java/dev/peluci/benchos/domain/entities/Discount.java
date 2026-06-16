package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Discount extends BaseEntity {

    private UUID tenantId;
    private UUID serviceOrderId;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private boolean approved;

    public Discount(UUID tenantId, UUID serviceOrderId, BigDecimal requestedAmount, BigDecimal approvedAmount, boolean approved) {
        super();
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.requestedAmount = requestedAmount;
        this.approvedAmount = approvedAmount;
        this.approved = approved;
    }

    public Discount(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                    UUID tenantId, UUID serviceOrderId, BigDecimal requestedAmount, BigDecimal approvedAmount, boolean approved) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.requestedAmount = requestedAmount;
        this.approvedAmount = approvedAmount;
        this.approved = approved;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getServiceOrderId() {
        return serviceOrderId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public boolean isApproved() {
        return approved;
    }
}
