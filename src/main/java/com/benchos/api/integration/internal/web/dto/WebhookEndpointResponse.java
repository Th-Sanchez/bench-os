package com.benchos.api.integration.internal.web.dto;

import com.benchos.api.integration.internal.entity.WebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        String name,
        String targetUrl,
        List<String> events,
        boolean active,
        String secretMasked,
        String signingSecret,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookEndpointResponse from(WebhookEndpoint endpoint, String revealedSecretOnce) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getTargetUrl(),
                endpoint.getSubscribedEvents(),
                endpoint.isActive(),
                endpoint.getMaskedSecret(),
                revealedSecretOnce,
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}
