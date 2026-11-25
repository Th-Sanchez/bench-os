package com.benchos.api.shared.ratelimit;

/**
 * Abstraction for request rate limiting, enabling in-memory enforcement for single-instance
 * deployments and future distributed backends (e.g. Redis) without changing filter logic.
 */
public interface RateLimiter {

    RateLimitDecision tryConsume(String key, RateLimitRule rule);

    void clear();
}
