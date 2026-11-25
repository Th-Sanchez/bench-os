package com.benchos.api.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, memory-bounded in-memory token bucket rate limiter.
 *
 * <p>Each key gets an independent bucket synchronized only on itself (no global lock on the hot path).
 * When the map reaches {@code maxEntries}, expired/idle buckets are purged first and, if still full,
 * the oldest-accessed entries are evicted so IP-rotation floods cannot exhaust JVM heap.</p>
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Clock clock;
    private final int maxEntries;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger opsSinceCleanup = new AtomicInteger(0);

    public InMemoryRateLimiter(Clock clock, int maxEntries) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public RateLimitDecision tryConsume(String key, RateLimitRule rule) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(rule, "rule must not be null");

        Instant now = clock.instant();
        maybeEvictStaleEntries(now);

        TokenBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || !existing.matches(rule)) {
                return new TokenBucket(rule.maxRequests(), rule.window(), now);
            }
            return existing;
        });

        return bucket.tryConsume(now);
    }

    @Override
    public void clear() {
        buckets.clear();
        opsSinceCleanup.set(0);
    }

    int activeBucketCount() {
        return buckets.size();
    }

    private void maybeEvictStaleEntries(Instant now) {
        boolean periodicSweep = opsSinceCleanup.incrementAndGet() % 512 == 0;
        boolean overCapacity = buckets.size() >= maxEntries;
        if (!periodicSweep && !overCapacity) {
            return;
        }

        buckets.entrySet().removeIf(entry -> entry.getValue().isIdleAndFull(now));

        if (buckets.size() >= maxEntries) {
            int targetRemoval = Math.max(1, buckets.size() - (int) (maxEntries * 0.8));
            List<String> oldestKeys = buckets.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getValue().lastAccess()))
                    .limit(targetRemoval)
                    .map(Map.Entry::getKey)
                    .toList();
            oldestKeys.forEach(buckets::remove);
        }
    }

    private static final class TokenBucket {
        private final int capacity;
        private final Duration window;
        private final long nanosPerToken;
        private long availableTokens;
        private Instant lastRefill;
        private volatile Instant lastAccess;

        private TokenBucket(int capacity, Duration window, Instant now) {
            this.capacity = capacity;
            this.window = window;
            this.nanosPerToken = Math.max(1L, window.toNanos() / capacity);
            this.availableTokens = capacity;
            this.lastRefill = now;
            this.lastAccess = now;
        }

        private boolean matches(RateLimitRule rule) {
            return this.capacity == rule.maxRequests() && this.window.equals(rule.window());
        }

        private Instant lastAccess() {
            return lastAccess;
        }

        private synchronized boolean isIdleAndFull(Instant now) {
            refill(now);
            return availableTokens >= capacity && !Duration.between(lastAccess, now).minus(window).isNegative();
        }

        private synchronized RateLimitDecision tryConsume(Instant now) {
            refill(now);
            this.lastAccess = now;

            if (availableTokens > 0) {
                availableTokens--;
                return RateLimitDecision.allowed(capacity, availableTokens);
            }

            long elapsedSinceRefill = Math.max(0L, Duration.between(lastRefill, now).toNanos());
            long waitNanos = Math.max(1L, nanosPerToken - elapsedSinceRefill);
            long retryAfterSeconds = Math.max(1L, (waitNanos + NANOS_PER_SECOND - 1L) / NANOS_PER_SECOND);
            return RateLimitDecision.rejected(capacity, retryAfterSeconds);
        }

        private void refill(Instant now) {
            long elapsedNanos = Duration.between(lastRefill, now).toNanos();
            if (elapsedNanos <= 0L) {
                return;
            }
            long tokensToAdd = elapsedNanos / nanosPerToken;
            if (tokensToAdd <= 0L) {
                return;
            }
            long nextTokens = availableTokens + tokensToAdd;
            if (nextTokens >= capacity) {
                availableTokens = capacity;
                lastRefill = now;
            } else {
                availableTokens = nextTokens;
                lastRefill = lastRefill.plusNanos(tokensToAdd * nanosPerToken);
            }
        }
    }
}
