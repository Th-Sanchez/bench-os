package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.ServiceOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class ServiceOrder extends BaseEntity {

    private UUID tenantId;
    private UUID customerId;
    private UUID attendantId;
    private String osCode;
    private ServiceOrderStatus status;
    private String deviceModel;
    private String deviceVoltage;
    private LocalDate receivedAt;
    private LocalDate deadlineAt;
    private boolean discardAuthorized;
    private String notes;

    public ServiceOrder(UUID tenantId, UUID customerId, UUID attendantId, String osCode, ServiceOrderStatus status, String deviceModel,
                        String deviceVoltage, LocalDate receivedAt, LocalDate deadlineAt, boolean discardAuthorized, String notes) {
        super();
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.attendantId = attendantId;
        this.osCode = osCode;
        this.status = status;
        this.deviceModel = deviceModel;
        this.deviceVoltage = deviceVoltage;
        this.receivedAt = receivedAt;
        this.deadlineAt = deadlineAt;
        this.discardAuthorized = discardAuthorized;
        this.notes = notes;
    }

    public ServiceOrder(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                        UUID tenantId, UUID customerId, UUID attendantId, String osCode, ServiceOrderStatus status, String deviceModel,
                        String deviceVoltage, LocalDate receivedAt, LocalDate deadlineAt, boolean discardAuthorized, String notes) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.attendantId = attendantId;
        this.osCode = osCode;
        this.status = status;
        this.deviceModel = deviceModel;
        this.deviceVoltage = deviceVoltage;
        this.receivedAt = receivedAt;
        this.deadlineAt = deadlineAt;
        this.discardAuthorized = discardAuthorized;
        this.notes = notes;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getAttendantId() {
        return attendantId;
    }

    public String getOsCode() {
        return osCode;
    }

    public ServiceOrderStatus getStatus() {
        return status;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getDeviceVoltage() {
        return deviceVoltage;
    }

    public LocalDate getReceivedAt() {
        return receivedAt;
    }

    public LocalDate getDeadlineAt() {
        return deadlineAt;
    }

    public boolean isDiscardAuthorized() {
        return discardAuthorized;
    }

    public String getNotes() {
        return notes;
    }
}
