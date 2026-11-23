package com.benchos.api.order.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchos.public-tracking")
public record PublicTrackingProperties(String baseUrl) {

    public PublicTrackingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://app.benchos.com/track";
        } else if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }
}
