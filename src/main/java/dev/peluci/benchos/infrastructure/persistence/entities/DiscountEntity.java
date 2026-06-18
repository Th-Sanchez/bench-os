package dev.peluci.benchos.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "discounts")
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "service_order_id", nullable = false, unique = true)
    private UUID serviceOrderId;

    @Column(
        name = "requested_amount",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    @Column(nullable = false)
    private boolean approved;
}
