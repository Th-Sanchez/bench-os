package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        String reportedIssue,
        String technicalDiagnosis,
        ItemStatus status,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal discount,
        BigDecimal totalPrice,
        LocalDate estimatedCompletionDate,
        int warrantyDays,
        String cancellationReason,
        Instant approvedAt,
        Instant finishedAt,
        Instant deliveredAt,
        Instant discardedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceOrderItemResponse from(ServiceOrderItem item, long orderNumber) {
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
                item.getReportedIssue(),
                item.getTechnicalDiagnosis(),
                item.getStatus(),
                item.getLaborCost(),
                item.getPartsCost(),
                item.getDiscount(),
                item.getTotalPrice(),
                item.getEstimatedCompletionDate(),
                item.getWarrantyDays(),
                item.getCancellationReason(),
                item.getApprovedAt(),
                item.getFinishedAt(),
                item.getDeliveredAt(),
                item.getDiscardedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
