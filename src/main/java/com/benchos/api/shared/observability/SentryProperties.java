package com.benchos.api.shared.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchos.sentry")
public record SentryProperties(
        String dsn,
        String environment,
        String release
) {
    public boolean isConfigured() {
        return dsn != null && !dsn.isBlank();
    }
}
