package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.PaymentMethod;
import dev.peluci.benchos.domain.enums.PaymentSource;
import dev.peluci.benchos.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

public class Payment extends BaseEntity {

    private ServiceOrder serviceOrder;
    private PaymentMethod method;
    private PaymentSource source;
    private PaymentStatus status;
    private BigDecimal amount;
    private Timestamp paidAt;

    public Payment(ServiceOrder serviceOrder, PaymentMethod method, PaymentSource source, PaymentStatus status, BigDecimal amount, Timestamp paidAt) {
        super();
        this.serviceOrder = serviceOrder;
        this.method = method;
        this.source = source;
        this.status = status;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Payment(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                   ServiceOrder serviceOrder, PaymentMethod method, PaymentSource source, PaymentStatus status, BigDecimal amount, Timestamp paidAt) {
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

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentSource getSource() {
        return source;
    }

    public void setSource(PaymentSource source) {
        this.source = source;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
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
