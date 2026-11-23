package com.benchos.api.shared.storage;

import java.time.Instant;

public record PresignedUploadUrl(
        String storageKey,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
