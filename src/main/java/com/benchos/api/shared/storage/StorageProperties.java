package com.benchos.api.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "benchos.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        boolean pathStyleAccess
) {
    public StorageProperties {
        if (region == null || region.isBlank()) {
            region = "auto";
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = "benchos-media";
        }
        if (uploadUrlTtl == null || uploadUrlTtl.isNegative() || uploadUrlTtl.isZero()) {
            uploadUrlTtl = Duration.ofMinutes(10);
        }
        if (downloadUrlTtl == null || downloadUrlTtl.isNegative() || downloadUrlTtl.isZero()) {
            downloadUrlTtl = Duration.ofHours(1);
        }
    }
}
