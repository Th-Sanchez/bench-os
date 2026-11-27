package com.benchos.api.integration;

import java.util.Arrays;
import java.util.Optional;

/**
 * Supported outbound webhook event types dispatched to tenant-configured endpoints.
 */
public enum WebhookEventType {
    ORDER_CREATED("order.created", false),
    ITEM_QUOTE_SENT("item.quote_sent", false),
    ITEM_APPROVED("item.approved", false),
    ITEM_READY_FOR_PICKUP("item.ready_for_pickup", false),
    ITEM_DELIVERED("item.delivered", false),
    PART_REQUEST_ARRIVED("part_request.arrived", false),
    WEBHOOK_TEST("webhook.test", true);

    private final String eventName;
    private final boolean systemTestOnly;

    WebhookEventType(String eventName, boolean systemTestOnly) {
        this.eventName = eventName;
        this.systemTestOnly = systemTestOnly;
    }

    public String eventName() {
        return eventName;
    }

    public boolean isSystemTestOnly() {
        return systemTestOnly;
    }

    public static Optional<WebhookEventType> fromEventName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.eventName.equals(normalized) || type.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
