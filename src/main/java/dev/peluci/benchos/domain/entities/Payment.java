package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.PaymentMethod;
import dev.peluci.benchos.domain.enums.PaymentSource;
import dev.peluci.benchos.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Payment extends BaseEntity {

    private UUID tenantId;
    private UUID serviceOrderId;
    private PaymentMethod method;
    private PaymentSource source;
    private PaymentStatus status;
    private BigDecimal amount;
    private LocalDateTime paidAt;

    public Payment(UUID tenantId, UUID serviceOrderId, PaymentMethod method, PaymentSource source, PaymentStatus status,
                   BigDecimal amount, LocalDateTime paidAt) {
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.method = method;
        this.source = source;
        this.status = status;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Payment(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                   UUID tenantId, UUID serviceOrderId, PaymentMethod method, PaymentSource source, PaymentStatus status,
                   BigDecimal amount, LocalDateTime paidAt) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.serviceOrderId = serviceOrderId;
        this.method = method;
        this.source = source;
        this.status = status;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getServiceOrderId() {
        return serviceOrderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentSource getSource() {
        return source;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
