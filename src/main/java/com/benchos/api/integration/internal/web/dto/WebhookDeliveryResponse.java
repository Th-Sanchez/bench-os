package com.benchos.api.integration.internal.web.dto;

import com.benchos.api.integration.internal.entity.WebhookDelivery;
import com.benchos.api.integration.internal.entity.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        UUID webhookEndpointId,
        String eventType,
        UUID eventId,
        WebhookDeliveryStatus status,
        Integer httpStatus,
        Integer durationMs,
        String responseSnippet,
        String errorMessage,
        int attemptCount,
        String payloadJson,
        Instant createdAt,
        Instant deliveredAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getWebhookEndpointId(),
                delivery.getEventType(),
                delivery.getEventId(),
                delivery.getStatus(),
                delivery.getHttpStatus(),
                delivery.getDurationMs(),
                delivery.getResponseSnippet(),
                delivery.getErrorMessage(),
                delivery.getAttemptCount(),
                delivery.getPayloadJson(),
                delivery.getCreatedAt(),
                delivery.getDeliveredAt()
        );
    }
}
