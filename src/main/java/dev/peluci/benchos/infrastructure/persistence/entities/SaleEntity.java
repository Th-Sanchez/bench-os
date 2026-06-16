package dev.peluci.benchos.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Getter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

}
