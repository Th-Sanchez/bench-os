package com.benchos.api.order.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "benchos.public-tracking")
public record PublicTrackingProperties(
        String baseUrl,
        Boolean requireHttps
) {
    public PublicTrackingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://app.benchos.com/track";
        } else {
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
        if (requireHttps == null) {
            requireHttps = Boolean.FALSE;
        }
        if (Boolean.TRUE.equals(requireHttps) && !baseUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException(
                    "benchos.public-tracking.base-url must use https:// when require-https is enabled"
            );
        }
    }

    public boolean isRequireHttps() {
        return Boolean.TRUE.equals(requireHttps);
    }
}
