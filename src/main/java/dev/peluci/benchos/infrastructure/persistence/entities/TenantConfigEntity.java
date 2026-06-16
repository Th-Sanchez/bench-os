package dev.peluci.benchos.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tenant_configs")
@Getter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "max_orders_per_day", nullable = false)
    private int maxOrdersPerDay;

    @Column(name = "deadline_days_after_approval", nullable = false)
    private int deadlineDaysAfterApproval;

    @Column(name = "working_days", nullable = false)
    private String workingDays;

}
