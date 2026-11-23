package com.benchos.api.order;

import java.math.BigDecimal;

/**
 * Represents a single payment split used when settling a repaired order item on delivery.
 */
public record PaymentPart(
        PaymentMethod paymentMethod,
        BigDecimal amount
) {
}
