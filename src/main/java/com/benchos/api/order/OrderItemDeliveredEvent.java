package com.benchos.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published when a repaired order item is delivered with payment,
 * enabling downstream modules (e.g., finance) to record cash inflows asynchronously.
 */
public record OrderItemDeliveredEvent(
        UUID tenantId,
        UUID serviceOrderId,
        UUID itemId,
        long orderNumber,
        int itemIndex,
        BigDecimal totalPrice,
        List<PaymentPart> payments,
        Instant deliveredAt
) {
    public OrderItemDeliveredEvent {
        payments = payments == null ? List.of() : List.copyOf(payments);
    }
}
