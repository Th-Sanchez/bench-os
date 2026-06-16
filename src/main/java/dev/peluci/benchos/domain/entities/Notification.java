package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification extends BaseEntity {

    private UUID tenantId;
    private UUID userId;
    private String message;
    private boolean read;

    public Notification(UUID tenantId, UUID userId, String message, boolean read) {
        super();
        this.tenantId = tenantId;
        this.userId = userId;
        this.message = message;
        this.read = read;
    }

    public Notification(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                        UUID tenantId, UUID userId, String message, boolean read) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.userId = userId;
        this.message = message;
        this.read = read;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }
}
