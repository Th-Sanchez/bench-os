package com.benchos.api.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "benchos.security.jwt")
public record JwtProperties(
        String secret,
        Duration expiration,
        String issuer
) {

    private static final int MIN_HS256_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_HS256_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "benchos.security.jwt.secret must be at least 32 bytes (256 bits) for HS256 signing"
            );
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("benchos.security.jwt.expiration must be a positive duration");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "https://api.benchos.com";
        }
    }
}
