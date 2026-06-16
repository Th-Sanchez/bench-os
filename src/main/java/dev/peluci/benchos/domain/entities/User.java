package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public class User extends BaseEntity {

    private String email;
    private String passwordHash;
    private UserRole role;

    public User(String email, String passwordHash, UserRole role) {
        super();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public User(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                String email, String passwordHash, UserRole role) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
