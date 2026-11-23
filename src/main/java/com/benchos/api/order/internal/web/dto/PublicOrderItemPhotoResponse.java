package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PhotoStage;
import com.benchos.api.order.internal.entity.ItemPhoto;
import com.benchos.api.shared.storage.PresignedDownloadUrl;

import java.time.Instant;
import java.util.UUID;

/**
 * Sanitized customer-facing view of an item photo with a time-limited presigned view URL.
 * Intentionally omits internal storage_key details.
 */
public record PublicOrderItemPhotoResponse(
        UUID id,
        PhotoStage stage,
        String viewUrl,
        Instant viewUrlExpiresAt,
        Instant createdAt
) {
    public static PublicOrderItemPhotoResponse from(ItemPhoto photo, PresignedDownloadUrl downloadUrl) {
        return new PublicOrderItemPhotoResponse(
                photo.getId(),
                photo.getStage(),
                downloadUrl.downloadUrl(),
                downloadUrl.expiresAt(),
                photo.getCreatedAt()
        );
    }
}
