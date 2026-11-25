package com.benchos.api.shared.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    @Test
    void enforcesBurstLimitAndRefillsOverTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T10:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock, 1_000);
        RateLimitRule rule = new RateLimitRule("login:account", 3, Duration.ofSeconds(30)); // 1 token per 10s

        assertThat(limiter.tryConsume("login:a@shop.com", rule))
                .satisfies(d -> {
                    assertThat(d.allowed()).isTrue();
                    assertThat(d.limit()).isEqualTo(3);
                    assertThat(d.remaining()).isEqualTo(2);
                });
        assertThat(limiter.tryConsume("login:a@shop.com", rule).remaining()).isEqualTo(1);
        assertThat(limiter.tryConsume("login:a@shop.com", rule).remaining()).isEqualTo(0);

        // 4th immediate request is rejected with Retry-After = 10s
        RateLimitDecision rejected = limiter.tryConsume("login:a@shop.com", rule);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isEqualTo(0);
        assertThat(rejected.retryAfterSeconds()).isEqualTo(10);

        // After 4s still rejected with Retry-After = 6s
        clock.advance(Duration.ofSeconds(4));
        RateLimitDecision stillRejected = limiter.tryConsume("login:a@shop.com", rule);
        assertThat(stillRejected.allowed()).isFalse();
        assertThat(stillRejected.retryAfterSeconds()).isEqualTo(6);

        // After another 6s (total 10s), 1 token has refilled
        clock.advance(Duration.ofSeconds(6));
        RateLimitDecision allowedAgain = limiter.tryConsume("login:a@shop.com", rule);
        assertThat(allowedAgain.allowed()).isTrue();
        assertThat(allowedAgain.remaining()).isEqualTo(0);

        // Distinct keys do not share quota
        RateLimitDecision otherKey = limiter.tryConsume("login:b@shop.com", rule);
        assertThat(otherKey.allowed()).isTrue();
        assertThat(otherKey.remaining()).isEqualTo(2);
    }

    @Test
    void boundsMemoryUsageUnderIpRotationFlood() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T10:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock, 50);
        RateLimitRule rule = new RateLimitRule("public:ip", 5, Duration.ofMinutes(1));

        for (int i = 0; i < 200; i++) {
            clock.advance(Duration.ofMillis(10));
            limiter.tryConsume("ip:203.0.113." + i, rule);
        }

        assertThat(limiter.activeBucketCount()).isLessThanOrEqualTo(50);

        // After full idle window, stale entries are purged on next eviction
        clock.advance(Duration.ofMinutes(2));
        for (int i = 0; i < 512; i++) {
            limiter.tryConsume("ip:198.51.100.1", rule);
        }
        assertThat(limiter.activeBucketCount()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(now -> now.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
