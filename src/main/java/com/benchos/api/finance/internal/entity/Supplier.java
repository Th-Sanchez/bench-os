package com.benchos.api.finance.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier extends TenantAwareEntity {

    private static final int MIN_PHONE_DIGITS = 8;
    private static final int MAX_PHONE_DIGITS = 20;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Supplier() {
    }

    public Supplier(UUID tenantId, String name, String rawPhone, String notes) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.name = name.trim();
        this.phone = normalizeOptionalPhone(rawPhone);
        this.notes = normalizeOptional(notes);
        this.createdAt = Instant.now();
    }

    public static String normalizeOptionalPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() < MIN_PHONE_DIGITS || digits.length() > MAX_PHONE_DIGITS) {
            throw new BusinessRuleException(
                    "Phone number must contain between " + MIN_PHONE_DIGITS + " and " + MAX_PHONE_DIGITS + " digits"
            );
        }
        return digits;
    }

    public void update(String name, String rawPhone, String notes) {
        this.name = name.trim();
        this.phone = normalizeOptionalPhone(rawPhone);
        this.notes = normalizeOptional(notes);
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
