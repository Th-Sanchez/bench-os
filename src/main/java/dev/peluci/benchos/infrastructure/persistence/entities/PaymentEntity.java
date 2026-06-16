package dev.peluci.benchos.infrastructure.persistence.entities;

import dev.peluci.benchos.domain.enums.PaymentMethod;
import dev.peluci.benchos.domain.enums.PaymentSource;
import dev.peluci.benchos.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

}
