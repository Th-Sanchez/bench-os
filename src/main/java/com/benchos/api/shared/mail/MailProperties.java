package com.benchos.api.shared.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "benchos.mail")
public record MailProperties(
        String provider,
        String from,
        String resendApiKey,
        String resendBaseUrl,
        Duration resetCodeTtl,
        Duration resendCooldown
) {
    public MailProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        }
        if (from == null || from.isBlank()) {
            from = "BenchOS <onboarding@resend.dev>";
        }
        if (resendApiKey == null) {
            resendApiKey = "";
        }
        if (resendBaseUrl == null || resendBaseUrl.isBlank()) {
            resendBaseUrl = "https://api.resend.com";
        }
        if (resetCodeTtl == null || resetCodeTtl.isNegative() || resetCodeTtl.isZero()) {
            resetCodeTtl = Duration.ofMinutes(15);
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            resendCooldown = Duration.ofSeconds(60);
        }
    }
}
