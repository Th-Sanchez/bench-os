package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PhotoStage;

import java.time.Instant;

public record PresignPhotoUploadResponse(
        String storageKey,
        String uploadUrl,
        String contentType,
        PhotoStage stage,
        Instant expiresAt
) {
}
