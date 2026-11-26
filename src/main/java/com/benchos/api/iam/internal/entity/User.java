package com.benchos.api.iam.internal.entity;

import com.benchos.api.iam.Role;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends TenantAwareEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(UUID tenantId, String name, String email, String passwordHash, Role role) {
        this(tenantId, name, email, passwordHash, role, BigDecimal.ZERO);
    }

    public User(
            UUID tenantId,
            String name,
            String email,
            String passwordHash,
            Role role,
            BigDecimal commissionPercent
    ) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.name = name.trim();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.commissionPercent = validateCommissionPercent(commissionPercent);
        this.createdAt = Instant.now();
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateCommissionPercent(BigDecimal commissionPercent) {
        this.commissionPercent = validateCommissionPercent(commissionPercent);
    }

    public BigDecimal getCommissionPercent() {
        return commissionPercent != null ? commissionPercent : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal validateCommissionPercent(BigDecimal raw) {
        if (raw == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal scaled = raw.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0 || scaled.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("Commission percent must be between 0 and 100");
        }
        return scaled;
    }

    public UUID getId() {
        return id;
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

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
