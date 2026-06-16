package dev.peluci.benchos.domain.entities;

import dev.peluci.benchos.domain.enums.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public class User extends BaseEntity {

    private UUID tenantId;
    private String name;
    private String email;
    private String passwordHash;
    private UserRole role;
    private boolean firstLogin;

    public User(UUID tenantId, String name, String email, String passwordHash, UserRole role, boolean firstLogin) {
        super();
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.firstLogin = firstLogin;
    }

    public User(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                UUID tenantId, String name, String email, String passwordHash, UserRole role, boolean firstLogin) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.firstLogin = firstLogin;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }
}
