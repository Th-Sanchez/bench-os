package com.benchos.api.shared.ratelimit;

/**
 * Result of evaluating a single rate-limit bucket check.
 */
public record RateLimitDecision(
        boolean allowed,
        int limit,
        long remaining,
        long retryAfterSeconds
) {
    public static RateLimitDecision allowed(int limit, long remaining) {
        return new RateLimitDecision(true, limit, Math.max(0L, remaining), 0L);
    }

    public static RateLimitDecision rejected(int limit, long retryAfterSeconds) {
        return new RateLimitDecision(false, limit, 0L, Math.max(1L, retryAfterSeconds));
    }
}
