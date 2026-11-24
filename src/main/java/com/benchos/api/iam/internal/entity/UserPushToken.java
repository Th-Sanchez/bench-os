package com.benchos.api.iam.internal.entity;

import com.benchos.api.iam.DevicePlatform;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_push_tokens")
public class UserPushToken extends TenantAwareEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expo_push_token", nullable = false, unique = true, length = 255)
    private String expoPushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPushToken() {
    }

    public UserPushToken(
            UUID tenantId,
            UUID userId,
            String expoPushToken,
            DevicePlatform platform,
            String deviceName,
            Instant now
    ) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.userId = userId;
        this.expoPushToken = expoPushToken.trim();
        this.platform = platform;
        this.deviceName = normalizeDeviceName(deviceName);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(DevicePlatform platform, String deviceName, Instant now) {
        this.platform = platform;
        this.deviceName = normalizeDeviceName(deviceName);
        this.updatedAt = now;
    }

    private static String normalizeDeviceName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getExpoPushToken() {
        return expoPushToken;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
