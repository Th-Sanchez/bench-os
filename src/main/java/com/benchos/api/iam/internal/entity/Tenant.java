package com.benchos.api.iam.internal.entity;

import com.benchos.api.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String document;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    public Tenant(String name, String document, String phone) {
        this.id = UuidV7Generator.next();
        this.name = name.trim();
        this.document = document.trim();
        this.phone = phone.trim();
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDocument() {
        return document;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
