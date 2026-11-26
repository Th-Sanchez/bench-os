package com.benchos.api.order.internal.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkshopAnalyticsResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        String zone,
        // Quote conversion funnel
        long quotesSentCount,
        long quotesApprovedCount,
        long quotesRejectedCount,
        long quotesPendingCount,
        BigDecimal quoteApprovalRatePercent,
        // SLA / Turnaround (in hours)
        BigDecimal avgIntakeToQuoteHours,
        long intakeToQuoteSampleSize,
        BigDecimal avgApprovalToReadyHours,
        long approvalToReadySampleSize,
        BigDecimal avgTotalTurnaroundHours,
        // Financial & Ticket quality (delivered items in period)
        long deliveredItemsCount,
        long paidRepairsCount,
        BigDecimal totalRevenue,
        BigDecimal totalPartsCostActual,
        BigDecimal netRepairProfit,
        BigDecimal netProfitMarginPercent,
        BigDecimal averageTicket,
        // Quality / Warranty returns (items received in period)
        long itemsReceivedCount,
        long warrantyReturnsCount,
        BigDecimal warrantyReturnRatePercent,
        // Current live workshop bottlenecks snapshot
        long overdueActiveCount,
        long awaitingQuoteCount,
        long awaitingApprovalCount,
        long waitingPartsCount,
        long readyForPickupCount
) {
}
