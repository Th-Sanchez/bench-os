package dev.peluci.benchos.domain.entities;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TenantConfig extends BaseEntity {

    private UUID tenantId;
    private int maxOrdersPerDay;
    private int deadlineDaysAfterApproval;
    private List<DayOfWeek> workingDays;

    public TenantConfig(UUID tenantId, int maxOrdersPerDay, int deadlineDaysAfterApproval, List<DayOfWeek> workingDays) {
        super();
        this.tenantId = tenantId;
        this.maxOrdersPerDay = maxOrdersPerDay;
        this.deadlineDaysAfterApproval = deadlineDaysAfterApproval;
        this.workingDays = workingDays;
    }

    public TenantConfig(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                        UUID tenantId, int maxOrdersPerDay, int deadlineDaysAfterApproval, List<DayOfWeek> workingDays) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.maxOrdersPerDay = maxOrdersPerDay;
        this.deadlineDaysAfterApproval = deadlineDaysAfterApproval;
        this.workingDays = workingDays;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public int getMaxOrdersPerDay() {
        return maxOrdersPerDay;
    }

    public int getDeadlineDaysAfterApproval() {
        return deadlineDaysAfterApproval;
    }

    public List<DayOfWeek> getWorkingDays() {
        return workingDays;
    }
}
