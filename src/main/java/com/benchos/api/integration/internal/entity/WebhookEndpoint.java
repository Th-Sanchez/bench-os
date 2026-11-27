package com.benchos.api.integration.internal.entity;

import com.benchos.api.integration.WebhookEventType;
import com.benchos.api.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    @Column(name = "signing_secret", nullable = false, length = 128)
    private String signingSecret;

    @Column(name = "events_csv", nullable = false, length = 400)
    private String eventsCsv;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(
            UUID tenantId,
            String name,
            String targetUrl,
            String signingSecret,
            List<WebhookEventType> events,
            boolean active,
            Instant now
    ) {
        this.id = UuidV7Generator.next();
        this.tenantId = tenantId;
        this.name = name.trim();
        this.targetUrl = targetUrl.trim();
        this.signingSecret = signingSecret;
        this.eventsCsv = serializeEvents(events);
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String targetUrl,
            List<WebhookEventType> events,
            Boolean active,
            Instant now
    ) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (targetUrl != null && !targetUrl.isBlank()) {
            this.targetUrl = targetUrl.trim();
        }
        if (events != null && !events.isEmpty()) {
            this.eventsCsv = serializeEvents(events);
        }
        if (active != null) {
            this.active = active;
        }
        this.updatedAt = now;
    }

    public void rotateSecret(String newSecret, Instant now) {
        this.signingSecret = newSecret;
        this.updatedAt = now;
    }

    public List<String> getSubscribedEvents() {
        if (eventsCsv == null || eventsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(eventsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean subscribesTo(WebhookEventType eventType) {
        return getSubscribedEvents().contains(eventType.eventName());
    }

    public String getMaskedSecret() {
        if (signingSecret == null || signingSecret.length() <= 10) {
            return "whsec_••••";
        }
        String suffix = signingSecret.substring(signingSecret.length() - 4);
        return "whsec_••••••••" + suffix;
    }

    private static String serializeEvents(List<WebhookEventType> events) {
        return events.stream()
                .filter(e -> !e.isSystemTestOnly())
                .map(WebhookEventType::eventName)
                .distinct()
                .collect(Collectors.joining(","));
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public String getEventsCsv() {
        return eventsCsv;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
