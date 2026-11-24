package com.benchos.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a customer approves or rejects a quote via the public
 * tracking page ({@code /track/{publicToken}}), enabling real-time push notifications to the workshop.
 */
public record PublicQuoteRespondedEvent(
        UUID tenantId,
        UUID serviceOrderId,
        UUID itemId,
        long orderNumber,
        int itemIndex,
        String tag,
        String applianceLabel,
        String customerName,
        boolean approved,
        BigDecimal totalPrice,
        Instant respondedAt
) {
}
