package com.benchos.api.order.internal.web.dto;

import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.Voltage;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrderItemQueueResponse(
        UUID id,
        UUID serviceOrderId,
        long orderNumber,
        int itemIndex,
        String tag,
        UUID customerId,
        String customerName,
        String customerPhone,
        String applianceType,
        String brand,
        String model,
        Voltage voltage,
        String reportedIssue,
        ItemStatus status,
        BigDecimal totalPrice,
        LocalDate estimatedCompletionDate,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderItemQueueResponse from(ServiceOrderItem item, CustomerSnapshot customer) {
        ServiceOrder order = item.getServiceOrder();
        return new OrderItemQueueResponse(
                item.getId(),
                order.getId(),
                order.getOrderNumber(),
                item.getItemIndex(),
                item.getTag(),
                order.getCustomerId(),
                customer != null ? customer.name() : null,
                customer != null ? customer.phone() : null,
                item.getApplianceType(),
                item.getBrand(),
                item.getModel(),
                item.getVoltage(),
                item.getReportedIssue(),
                item.getStatus(),
                item.getTotalPrice(),
                item.getEstimatedCompletionDate(),
                item.getFinishedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
