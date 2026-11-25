package com.benchos.api.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "benchos.security.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Integer maxEntries,
        Boolean trustForwardedHeaders,
        AuthEndpointPolicy login,
        IpEndpointPolicy register,
        AuthEndpointPolicy forgotPassword,
        AuthEndpointPolicy resetPassword,
        IpEndpointPolicy publicRead,
        IpEndpointPolicy publicWrite
) {
    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    public RateLimitProperties {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (maxEntries == null || maxEntries < 100) {
            maxEntries = DEFAULT_MAX_ENTRIES;
        }
        if (trustForwardedHeaders == null) {
            trustForwardedHeaders = Boolean.FALSE;
        }
        if (login == null) {
            login = new AuthEndpointPolicy(20, Duration.ofMinutes(5), 10, Duration.ofMinutes(5));
        }
        if (register == null) {
            register = new IpEndpointPolicy(5, Duration.ofMinutes(15));
        }
        if (forgotPassword == null) {
            forgotPassword = new AuthEndpointPolicy(10, Duration.ofMinutes(15), 5, Duration.ofMinutes(15));
        }
        if (resetPassword == null) {
            resetPassword = new AuthEndpointPolicy(15, Duration.ofMinutes(15), 10, Duration.ofMinutes(15));
        }
        if (publicRead == null) {
            publicRead = new IpEndpointPolicy(60, Duration.ofMinutes(1));
        }
        if (publicWrite == null) {
            publicWrite = new IpEndpointPolicy(15, Duration.ofMinutes(5));
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isTrustForwardedHeaders() {
        return Boolean.TRUE.equals(trustForwardedHeaders);
    }

    public record IpEndpointPolicy(
            int ipMaxRequests,
            Duration ipWindow
    ) {
        public IpEndpointPolicy {
            if (ipMaxRequests <= 0) {
                ipMaxRequests = 10;
            }
            if (ipWindow == null || ipWindow.isZero() || ipWindow.isNegative()) {
                ipWindow = Duration.ofMinutes(5);
            }
        }

        public RateLimitRule ipRule(String name) {
            return new RateLimitRule(name + ":ip", ipMaxRequests, ipWindow);
        }
    }

    public record AuthEndpointPolicy(
            int ipMaxRequests,
            Duration ipWindow,
            int accountMaxRequests,
            Duration accountWindow
    ) {
        public AuthEndpointPolicy {
            if (ipMaxRequests <= 0) {
                ipMaxRequests = 20;
            }
            if (ipWindow == null || ipWindow.isZero() || ipWindow.isNegative()) {
                ipWindow = Duration.ofMinutes(5);
            }
            if (accountMaxRequests <= 0) {
                accountMaxRequests = 10;
            }
            if (accountWindow == null || accountWindow.isZero() || accountWindow.isNegative()) {
                accountWindow = Duration.ofMinutes(5);
            }
        }

        public RateLimitRule ipRule(String name) {
            return new RateLimitRule(name + ":ip", ipMaxRequests, ipWindow);
        }

        public RateLimitRule accountRule(String name) {
            return new RateLimitRule(name + ":account", accountMaxRequests, accountWindow);
        }
    }
}
