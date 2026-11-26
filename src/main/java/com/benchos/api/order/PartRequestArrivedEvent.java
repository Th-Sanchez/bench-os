package com.benchos.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a requested part arrives and the shop owner chooses
 * to automatically record a PARTS_PURCHASE expense in the finance module.
 */
public record PartRequestArrivedEvent(
        UUID tenantId,
        UUID partRequestId,
        UUID serviceOrderId,
        UUID itemId,
        long orderNumber,
        int itemIndex,
        String partName,
        int quantity,
        BigDecimal totalCost,
        String supplierName,
        PaymentMethod paymentMethod,
        Instant arrivedAt
) {
}
