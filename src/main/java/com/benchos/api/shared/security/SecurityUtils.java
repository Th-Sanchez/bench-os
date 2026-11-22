package com.benchos.api.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Convenience helpers to access the authenticated principal from application services.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user is bound to the current security context");
    }

    public static UUID requireCurrentUserId() {
        return requireCurrentUser().userId();
    }

    public static UUID requireCurrentTenantId() {
        return requireCurrentUser().tenantId();
    }
}
