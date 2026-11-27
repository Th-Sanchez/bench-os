package com.benchos.api.integration.internal.entity;

import com.benchos.api.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    private static final int MAX_SNIPPET_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "webhook_endpoint_id", nullable = false, updatable = false)
    private UUID webhookEndpointId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WebhookDeliveryStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "response_snippet", length = 500)
    private String responseSnippet;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(
            UUID tenantId,
            UUID webhookEndpointId,
            String eventType,
            UUID eventId,
            String payloadJson,
            Instant now
    ) {
        this.id = UuidV7Generator.next();
        this.tenantId = tenantId;
        this.webhookEndpointId = webhookEndpointId;
        this.eventType = eventType;
        this.eventId = eventId;
        this.payloadJson = payloadJson;
        this.status = WebhookDeliveryStatus.FAILED;
        this.attemptCount = 0;
        this.createdAt = now;
    }

    public void recordAttempt(
            boolean success,
            Integer httpStatus,
            int durationMs,
            String responseSnippet,
            String errorMessage,
            Instant now
    ) {
        this.attemptCount++;
        this.status = success ? WebhookDeliveryStatus.SUCCESS : WebhookDeliveryStatus.FAILED;
        this.httpStatus = httpStatus;
        this.durationMs = Math.max(0, durationMs);
        this.responseSnippet = truncate(responseSnippet);
        this.errorMessage = truncate(errorMessage);
        this.deliveredAt = now;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= MAX_SNIPPET_LENGTH ? cleaned : cleaned.substring(0, MAX_SNIPPET_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getWebhookEndpointId() {
        return webhookEndpointId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getResponseSnippet() {
        return responseSnippet;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
