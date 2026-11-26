package com.benchos.api.order.internal.web.dto;

import com.benchos.api.iam.CommissionBasis;
import com.benchos.api.iam.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MonthlyCommissionReportResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        String zone,
        boolean technicianAssignmentEnabled,
        CommissionBasis commissionBasis,
        long deliveredWithTechnicianCount,
        long deliveredUnassignedCount,
        BigDecimal totalCommissionableRevenue,
        BigDecimal totalCommissionAmount,
        List<TechnicianCommissionRow> technicians
) {
    public record TechnicianCommissionRow(
            UUID technicianId,
            String technicianName,
            Role role,
            BigDecimal defaultCommissionPercent,
            long deliveredItemsCount,
            BigDecimal totalLabor,
            BigDecimal totalRevenue,
            BigDecimal totalNetProfit,
            BigDecimal commissionTotal,
            List<CommissionItemDetail> items
    ) {
    }

    public record CommissionItemDetail(
            UUID serviceOrderId,
            UUID itemId,
            String itemTag,
            String applianceDisplayName,
            Instant deliveredAt,
            BigDecimal laborCost,
            BigDecimal totalPrice,
            BigDecimal netProfit,
            BigDecimal commissionPercent,
            BigDecimal commissionAmount
    ) {
    }
}
