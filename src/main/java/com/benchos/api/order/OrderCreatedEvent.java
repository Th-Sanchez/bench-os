package com.benchos.api.order;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a new service order (or warranty return order) is created.
 */
public record OrderCreatedEvent(
        UUID tenantId,
        UUID serviceOrderId,
        long orderNumber,
        UUID publicToken,
        UUID customerId,
        String customerName,
        int itemCount,
        Instant createdAt
) {
}
