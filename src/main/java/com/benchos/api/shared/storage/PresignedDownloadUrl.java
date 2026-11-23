package com.benchos.api.shared.storage;

import java.time.Instant;

public record PresignedDownloadUrl(
        String downloadUrl,
        Instant expiresAt
) {
}
