package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

public class Payment extends BaseEntity {

    private ServiceOrder serviceOrder;
    private String method;
    private String source;
    private String status;
    private BigDecimal amount;
    private Timestamp paidAt;

    public Payment(ServiceOrder serviceOrder, String method, String source, String status, BigDecimal amount, Timestamp paidAt) {
        super();
        this.serviceOrder = serviceOrder;
        this.method = method;
        this.source = source;
        this.status = status;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Payment(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                   ServiceOrder serviceOrder, String method, String source, String status, BigDecimal amount, Timestamp paidAt) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.serviceOrder = serviceOrder;
        this.method = method;
        this.source = source;
        this.status = status;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public ServiceOrder getServiceOrder() {
        return serviceOrder;
    }

    public void setServiceOrder(ServiceOrder serviceOrder) {
        this.serviceOrder = serviceOrder;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Timestamp getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Timestamp paidAt) {
        this.paidAt = paidAt;
    }
}
