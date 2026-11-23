package com.benchos.api.order.internal.entity;

import com.benchos.api.order.PhotoStage;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.id.UuidV7Generator;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "item_photos")
public class ItemPhoto extends TenantAwareEntity {

    public static final int MAX_PHOTOS_PER_ITEM = 20;

    private static final Map<String, String> SUPPORTED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$");

    @Id
    private UUID id;

    @Column(name = "service_order_item_id", nullable = false, updatable = false)
    private UUID serviceOrderItemId;

    @Column(name = "storage_key", nullable = false, unique = true, updatable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 50)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PhotoStage stage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ItemPhoto() {
    }

    public ItemPhoto(
            UUID tenantId,
            UUID serviceOrderItemId,
            String storageKey,
            String contentType,
            PhotoStage stage
    ) {
        super(tenantId);
        this.id = UuidV7Generator.next();
        this.serviceOrderItemId = serviceOrderItemId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.stage = stage;
        this.createdAt = Instant.now();
    }

    public static String validateAndNormalizeContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            throw new BusinessRuleException("Content type is required");
        }
        String normalized = rawContentType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_CONTENT_TYPES.containsKey(normalized)) {
            throw new BusinessRuleException(
                    "Unsupported image content type. Allowed types: image/jpeg, image/png, image/webp"
            );
        }
        return normalized;
    }

    public static String buildStorageKeyPrefix(UUID tenantId, UUID orderId, UUID itemId) {
        return "tenants/" + tenantId + "/orders/" + orderId + "/items/" + itemId + "/";
    }

    public static String generateStorageKey(UUID tenantId, UUID orderId, UUID itemId, String normalizedContentType) {
        String extension = SUPPORTED_CONTENT_TYPES.get(normalizedContentType);
        if (extension == null) {
            throw new BusinessRuleException("Unsupported image content type");
        }
        return buildStorageKeyPrefix(tenantId, orderId, itemId) + UuidV7Generator.next() + "." + extension;
    }

    public static void validateStorageKeyForItem(
            UUID tenantId,
            UUID orderId,
            UUID itemId,
            String storageKey,
            String normalizedContentType
    ) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw new BusinessRuleException("Invalid storage key for this service order item");
        }
        String expectedPrefix = buildStorageKeyPrefix(tenantId, orderId, itemId);
        if (!storageKey.startsWith(expectedPrefix)) {
            throw new BusinessRuleException("Storage key does not belong to this service order item");
        }
        String filename = storageKey.substring(expectedPrefix.length());
        String expectedExtension = SUPPORTED_CONTENT_TYPES.get(normalizedContentType);
        if (!SAFE_FILENAME_PATTERN.matcher(filename).matches()
                || expectedExtension == null
                || !filename.endsWith("." + expectedExtension)) {
            throw new BusinessRuleException("Invalid storage key format or extension mismatch");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceOrderItemId() {
        return serviceOrderItemId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public PhotoStage getStage() {
        return stage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
