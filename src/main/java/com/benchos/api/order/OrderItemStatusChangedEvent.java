package com.benchos.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when an order item transitions to a key lifecycle milestone
 * (e.g., QUOTE_SENT, APPROVED, READY_FOR_PICKUP).
 */
public record OrderItemStatusChangedEvent(
        UUID tenantId,
        UUID serviceOrderId,
        UUID itemId,
        long orderNumber,
        int itemIndex,
        String tag,
        String applianceLabel,
        ItemStatus status,
        BigDecimal totalPrice,
        String selectedQuoteOptionLabel,
        Instant changedAt
) {
}
