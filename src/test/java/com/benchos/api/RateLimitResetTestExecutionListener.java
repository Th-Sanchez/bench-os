package com.benchos.api;

import com.benchos.api.shared.ratelimit.RateLimiter;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Clears in-memory rate-limit buckets before each Spring test method so tests sharing a cached
 * {@code ApplicationContext} do not leak loopback (127.0.0.1) quotas into subsequent test methods.
 */
public class RateLimitResetTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (!testContext.hasApplicationContext()) {
            return;
        }
        testContext.getApplicationContext()
                .getBeanProvider(RateLimiter.class)
                .ifAvailable(RateLimiter::clear);
    }
}
