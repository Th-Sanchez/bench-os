package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class BaseEntity {

    private final UUID id;
    private boolean active;
    private final LocalDateTime dtInsert;
    private LocalDateTime dtUpdate;
    private LocalDateTime dtDelete;

    protected BaseEntity() {
        this.id = UUID.randomUUID();
        this.active = true;
        this.dtInsert = LocalDateTime.now();
        this.dtUpdate = LocalDateTime.now();
    }

    protected BaseEntity(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete) {
        this.id = id;
        this.active = active;
        this.dtInsert = dtInsert;
        this.dtUpdate = dtUpdate;
        this.dtDelete = dtDelete;
    }

    public void delete() {
        this.active = false;
        this.dtDelete = LocalDateTime.now();
        this.updateTimestamp();
    }

    protected void updateTimestamp() {
        this.dtUpdate = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getDtInsert() {
        return dtInsert;
    }

    public LocalDateTime getDtUpdate() {
        return dtUpdate;
    }

    public LocalDateTime getDtDelete() {
        return dtDelete;
    }
}
