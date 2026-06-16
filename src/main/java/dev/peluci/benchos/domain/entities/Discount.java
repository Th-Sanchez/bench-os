package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Discount extends BaseEntity {

    private ServiceOrder serviceOrder;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private Boolean approved;

    public Discount(ServiceOrder serviceOrder, BigDecimal requestedAmount, BigDecimal approvedAmount, Boolean approved) {
        super();
        this.serviceOrder = serviceOrder;
        this.requestedAmount = requestedAmount;
        this.approvedAmount = approvedAmount;
        this.approved = approved;
    }

    public Discount(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                    ServiceOrder serviceOrder, BigDecimal requestedAmount, BigDecimal approvedAmount, Boolean approved) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.serviceOrder = serviceOrder;
        this.requestedAmount = requestedAmount;
        this.approvedAmount = approvedAmount;
        this.approved = approved;
    }

    public ServiceOrder getServiceOrder() {
        return serviceOrder;
    }

    public void setServiceOrder(ServiceOrder serviceOrder) {
        this.serviceOrder = serviceOrder;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }
}
