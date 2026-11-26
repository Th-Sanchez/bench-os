package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
import com.benchos.api.order.internal.entity.IntakeChecklistEntry;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ServiceOrderItemResponse(
        UUID id,
        int itemIndex,
        String tag,
        String applianceType,
        String brand,
        String model,
        Voltage voltage,
        String serialNumber,
        String accessories,
        List<IntakeChecklistEntry> intakeChecklist,
        String reportedIssue,
        String technicalDiagnosis,
        ItemStatus status,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal partsCostActual,
        BigDecimal discount,
        BigDecimal totalPrice,
        BigDecimal estimatedNetProfit,
        LocalDate estimatedCompletionDate,
        int warrantyDays,
        String cancellationReason,
        Instant approvedAt,
        Instant finishedAt,
        Instant deliveredAt,
        String deliverySignatureSvg,
        String deliverySignerName,
        Instant discardedAt,
        UUID parentItemId,
        UUID parentOrderId,
        String parentItemTag,
        boolean warrantyReturn,
        UUID assignedTechnicianId,
        String assignedTechnicianName,
        BigDecimal commissionPercent,
        BigDecimal commissionAmount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceOrderItemResponse from(ServiceOrderItem item, long orderNumber) {
        return from(item, orderNumber, null, null, null, false);
    }

    public static ServiceOrderItemResponse from(
            ServiceOrderItem item,
            long orderNumber,
            UUID parentOrderId,
            String parentItemTag
    ) {
        return from(item, orderNumber, parentOrderId, parentItemTag, null, false);
    }

    public static ServiceOrderItemResponse from(
            ServiceOrderItem item,
            long orderNumber,
            UUID parentOrderId,
            String parentItemTag,
            String assignedTechnicianName,
            boolean includeCommission
    ) {
        return new ServiceOrderItemResponse(
                item.getId(),
                item.getItemIndex(),
                orderNumber + "-" + item.getItemIndex(),
                item.getApplianceType(),
                item.getBrand(),
                item.getModel(),
                item.getVoltage(),
                item.getSerialNumber(),
                item.getAccessories(),
                item.getIntakeChecklist(),
                item.getReportedIssue(),
                item.getTechnicalDiagnosis(),
                item.getStatus(),
                item.getLaborCost(),
                item.getPartsCost(),
                item.getPartsCostActual(),
                item.getDiscount(),
                item.getTotalPrice(),
                item.getEstimatedNetProfit(),
                item.getEstimatedCompletionDate(),
                item.getWarrantyDays(),
                item.getCancellationReason(),
                item.getApprovedAt(),
                item.getFinishedAt(),
                item.getDeliveredAt(),
                item.getDeliverySignatureSvg(),
                item.getDeliverySignerName(),
                item.getDiscardedAt(),
                item.getParentItemId(),
                parentOrderId,
                parentItemTag,
                item.isWarrantyReturn(),
                item.getAssignedTechnicianId(),
                assignedTechnicianName,
                includeCommission ? item.getCommissionPercent() : null,
                includeCommission ? item.getCommissionAmount() : null,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
