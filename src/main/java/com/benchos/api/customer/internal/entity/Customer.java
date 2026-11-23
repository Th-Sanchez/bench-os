package com.benchos.api.customer.internal.entity;

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
@Table(name = "customers")
public class Customer extends TenantAwareEntity {

    private static final int MIN_PHONE_DIGITS = 8;
    private static final int MAX_PHONE_DIGITS = 20;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 30)
    private String document;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Customer() {
    }

    public Customer(UUID tenantId, String name, String normalizedPhone, String document, String notes) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.name = name.trim();
        this.phone = normalizedPhone;
        this.document = normalizeOptional(document);
        this.notes = normalizeOptional(notes);
        this.createdAt = Instant.now();
    }

    public static String extractDigits(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.replaceAll("\\D", "");
    }

    public static String normalizePhone(String rawPhone) {
        String digits = extractDigits(rawPhone);
        if (digits.length() < MIN_PHONE_DIGITS || digits.length() > MAX_PHONE_DIGITS) {
            throw new BusinessRuleException(
                    "Phone number must contain between " + MIN_PHONE_DIGITS + " and " + MAX_PHONE_DIGITS + " digits"
            );
        }
        return digits;
    }

    public void update(String name, String normalizedPhone, String document, String notes) {
        this.name = name.trim();
        this.phone = normalizedPhone;
        this.document = normalizeOptional(document);
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

    public String getDocument() {
        return document;
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
