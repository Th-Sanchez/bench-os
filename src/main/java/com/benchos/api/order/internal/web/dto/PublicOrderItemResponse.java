package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sanitized customer-facing view of a service order item.
 */
public record PublicOrderItemResponse(
        int itemIndex,
        String tag,
        String applianceType,
        String brand,
        String model,
        Voltage voltage,
        String serialNumber,
        String accessories,
        String reportedIssue,
        ItemStatus status,
        String technicalDiagnosis,
        BigDecimal totalPrice,
        LocalDate estimatedCompletionDate,
        int warrantyDays,
        Instant approvedAt,
        Instant finishedAt,
        Instant deliveredAt,
        String deliverySignatureSvg,
        Instant discardedAt,
        List<PublicOrderItemPhotoResponse> photos
) {
    public static PublicOrderItemResponse from(
            ServiceOrderItem item,
            long orderNumber,
            List<PublicOrderItemPhotoResponse> photos
    ) {
        return new PublicOrderItemResponse(
                item.getItemIndex(),
                orderNumber + "-" + item.getItemIndex(),
                item.getApplianceType(),
                item.getBrand(),
                item.getModel(),
                item.getVoltage(),
                item.getSerialNumber(),
                item.getAccessories(),
                item.getReportedIssue(),
                item.getStatus(),
                item.getTechnicalDiagnosis(),
                item.getTotalPrice(),
                item.getEstimatedCompletionDate(),
                item.getWarrantyDays(),
                item.getApprovedAt(),
                item.getFinishedAt(),
                item.getDeliveredAt(),
                item.getDeliverySignatureSvg(),
                item.getDiscardedAt(),
                photos
        );
    }
}
