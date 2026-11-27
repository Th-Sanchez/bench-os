package com.benchos.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published unconditionally whenever a requested part is marked as ARRIVED,
 * allowing outbound integrations/webhooks to react even when no finance expense is recorded.
 */
public record OrderPartArrivedEvent(
        UUID tenantId,
        UUID partRequestId,
        UUID serviceOrderId,
        UUID itemId,
        long orderNumber,
        int itemIndex,
        String tag,
        String partName,
        int quantity,
        BigDecimal totalCost,
        String supplierName,
        ItemStatus resultingItemStatus,
        Instant arrivedAt
) {
}
