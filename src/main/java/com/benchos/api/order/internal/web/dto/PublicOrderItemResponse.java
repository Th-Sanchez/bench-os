package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Sanitized customer-facing view of a service order item.
 */
public record PublicOrderItemResponse(
        int itemIndex,
        String tag,
        String applianceType,
        String brand,
        String model,
        ItemStatus status,
        String technicalDiagnosis,
        BigDecimal totalPrice,
        LocalDate estimatedCompletionDate,
        int warrantyDays,
        Instant approvedAt,
        Instant finishedAt,
        Instant deliveredAt,
        Instant discardedAt
) {
    public static PublicOrderItemResponse from(ServiceOrderItem item, long orderNumber) {
        return new PublicOrderItemResponse(
                item.getItemIndex(),
                orderNumber + "-" + item.getItemIndex(),
                item.getApplianceType(),
                item.getBrand(),
                item.getModel(),
                item.getStatus(),
                item.getTechnicalDiagnosis(),
                item.getTotalPrice(),
                item.getEstimatedCompletionDate(),
                item.getWarrantyDays(),
                item.getApprovedAt(),
                item.getFinishedAt(),
                item.getDeliveredAt(),
                item.getDiscardedAt()
        );
    }
}
