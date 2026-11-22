package com.benchos.api.iam.internal.web.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        UserResponse user,
        TenantResponse tenant
) {
}
