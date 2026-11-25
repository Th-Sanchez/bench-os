package com.benchos.api.shared.ratelimit;

import java.time.Duration;

/**
 * Immutable rate-limit policy for a single key scope (e.g., per IP or per account).
 */
public record RateLimitRule(
        String name,
        int maxRequests,
        Duration window
) {
    public RateLimitRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Rate limit rule name must not be blank");
        }
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
