package dev.peluci.benchos.domain.entities;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

public class Customer extends BaseEntity {

    private String name;
    private String phone;
    private String accessToken;
    private Timestamp tokenExpiresAt;
    private String osCode;

    public Customer(String name, String phone, String accessToken, Timestamp tokenExpiresAt, String osCode) {
        super();
        this.name = name;
        this.phone = phone;
        this.accessToken = accessToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.osCode = osCode;
    }

    public Customer(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                    String name, String phone, String accessToken, Timestamp tokenExpiresAt, String osCode) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.name = name;
        this.phone = phone;
        this.accessToken = accessToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.osCode = osCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Timestamp getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Timestamp tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public String getOsCode() {
        return osCode;
    }

    public void setOsCode(String osCode) {
        this.osCode = osCode;
    }
}
