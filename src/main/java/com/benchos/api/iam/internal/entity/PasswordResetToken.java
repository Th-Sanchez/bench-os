package com.benchos.api.iam.internal.entity;

import com.benchos.api.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(UUID userId, String codeHash, Instant expiresAt, Instant createdAt) {
        this.id = UuidV7Generator.next();
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasExceededMaxAttempts() {
        return attempts >= MAX_ATTEMPTS;
    }

    public void incrementAttempts() {
        this.attempts += 1;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
