package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class BaseEntity {

    private UUID id;
    private Boolean active;
    private LocalDateTime dtInsert;
    private LocalDateTime dtUpdate;
    private LocalDateTime dtDelete;

    protected BaseEntity() {
        this.id = UUID.randomUUID();
        this.active = true;
        this.dtInsert = LocalDateTime.now();
        this.dtUpdate = LocalDateTime.now();
    }

    public BaseEntity(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete) {
        this.id = id;
        this.active = active;
        this.dtInsert = dtInsert;
        this.dtUpdate = dtUpdate;
        this.dtDelete = dtDelete;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getDtInsert() {
        return dtInsert;
    }

    public void setDtInsert(LocalDateTime dtInsert) {
        this.dtInsert = dtInsert;
    }

    public LocalDateTime getDtUpdate() {
        return dtUpdate;
    }

    public void setDtUpdate(LocalDateTime dtUpdate) {
        this.dtUpdate = dtUpdate;
    }

    public LocalDateTime getDtDelete() {
        return dtDelete;
    }

    public void setDtDelete(LocalDateTime dtDelete) {
        this.dtDelete = dtDelete;
    }
}
