package dev.peluci.benchos.infrastructure.persistence.entities;

import dev.peluci.benchos.domain.enums.ServiceOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "service_orders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"os_code", "tenant_id"})
)
@Getter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOrderEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "attendant_id", nullable = false)
    private UUID attendantId;

    @Column(name = "os_code", nullable = false)
    private String osCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceOrderStatus status;

    @Column(name = "device_model", nullable = false)
    private String deviceModel;

    @Column(name = "device_voltage", nullable = false)
    private String deviceVoltage;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(name = "deadline_at")
    private LocalDate deadlineAt;

    @Column(name = "discard_authorized", nullable = false)
    private boolean discardAuthorized;

    @Column(columnDefinition = "TEXT")
    private String notes;

}
