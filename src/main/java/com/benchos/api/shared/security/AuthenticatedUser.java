package com.benchos.api.shared.security;

import java.util.UUID;

/**
 * Immutable representation of the authenticated principal extracted from the JWT.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        String role
) {
}
