package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Customer extends BaseEntity {

    private UUID tenantId;
    private String name;
    private String phone;
    private String email;

    public Customer(UUID tenantId, String name, String phone, String email) {
        super();
        this.tenantId = tenantId;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public Customer(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                    UUID tenantId, String name, String phone, String email) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tenantId = tenantId;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
