package com.benchos.api.order.internal.web.dto;

import java.math.BigDecimal;

public record PartRequestSummaryResponse(
        long toOrderCount,
        long orderedCount,
        long arrivedCount,
        BigDecimal estimatedToOrderCost,
        BigDecimal estimatedOrderedCost
) {
}
