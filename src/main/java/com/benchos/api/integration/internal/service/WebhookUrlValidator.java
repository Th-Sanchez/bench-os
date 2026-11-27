package com.benchos.api.integration.internal.service;

import com.benchos.api.integration.internal.config.WebhookProperties;
import com.benchos.api.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Validates outbound webhook URLs and mitigates SSRF by requiring HTTPS and rejecting
 * loopback, link-local (cloud metadata), and private network targets in production.
 */
@Component
public class WebhookUrlValidator {

    private final WebhookProperties properties;

    public WebhookUrlValidator(WebhookProperties properties) {
        this.properties = properties;
    }

    public URI validateAndParse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessRuleException("Webhook URL is required");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > 500) {
            throw new BusinessRuleException("Webhook URL must be at most 500 characters");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid webhook URL format");
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessRuleException("Webhook URL must include a valid host");
        }

        if (!properties.isAllowPrivateTargets()) {
            if (!"https".equals(scheme)) {
                throw new BusinessRuleException("Webhook URL must use https://");
            }
            rejectPrivateOrLocalHost(host);
        } else if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BusinessRuleException("Webhook URL must use http:// or https://");
        }

        return uri;
    }

    private void rejectPrivateOrLocalHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lower)
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")) {
            throw new BusinessRuleException("Webhook URL cannot target localhost or internal hostnames");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrUnsafe(address)) {
                    throw new BusinessRuleException("Webhook URL cannot target private or link-local IP addresses");
                }
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Could not resolve webhook host: " + host);
        }
    }

    static boolean isPrivateOrUnsafe(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            // Carrier-grade NAT 100.64.0.0/10
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // Current network 0.0.0.0/8
            if (first == 0) {
                return true;
            }
        } else if (bytes.length == 16) {
            // IPv6 Unique Local Address fc00::/7
            int first = Byte.toUnsignedInt(bytes[0]);
            if ((first & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }
}
