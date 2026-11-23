package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PhotoStage;
import com.benchos.api.order.internal.entity.ItemPhoto;
import com.benchos.api.shared.storage.PresignedDownloadUrl;

import java.time.Instant;
import java.util.UUID;

public record ItemPhotoResponse(
        UUID id,
        UUID serviceOrderItemId,
        String storageKey,
        String contentType,
        PhotoStage stage,
        String viewUrl,
        Instant viewUrlExpiresAt,
        Instant createdAt
) {
    public static ItemPhotoResponse from(ItemPhoto photo, PresignedDownloadUrl downloadUrl) {
        return new ItemPhotoResponse(
                photo.getId(),
                photo.getServiceOrderItemId(),
                photo.getStorageKey(),
                photo.getContentType(),
                photo.getStage(),
                downloadUrl.downloadUrl(),
                downloadUrl.expiresAt(),
                photo.getCreatedAt()
        );
    }
}
