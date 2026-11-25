package com.benchos.api.order.internal.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RepairProfitabilityResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        String zone,
        int deliveredItemsCount,
        int itemsWithPartsCostCount,
        BigDecimal totalRevenue,
        BigDecimal totalPartsCostActual,
        BigDecimal estimatedNetProfit,
        BigDecimal profitMarginPercent
) {
}
