package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification extends BaseEntity {

    private User user;
    private String message;
    private Boolean read;

    public Notification(User user, String message, Boolean read) {
        super();
        this.user = user;
        this.message = message;
        this.read = read;
    }

    public Notification(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                        User user, String message, Boolean read) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.user = user;
        this.message = message;
        this.read = read;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }
}
