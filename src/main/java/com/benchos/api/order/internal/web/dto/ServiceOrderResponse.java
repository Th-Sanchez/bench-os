package com.benchos.api.order.internal.web.dto;

import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        UUID tenantId,
        long orderNumber,
        UUID publicToken,
        UUID customerId,
        String customerName,
        String customerPhone,
        UUID createdByUserId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<ServiceOrderItemResponse> items
) {
    public static ServiceOrderResponse from(
            ServiceOrder order,
            CustomerSnapshot customer,
            List<ServiceOrderItem> items
    ) {
        List<ServiceOrderItemResponse> itemResponses = items.stream()
                .map(item -> ServiceOrderItemResponse.from(item, order.getOrderNumber()))
                .toList();

        return new ServiceOrderResponse(
                order.getId(),
                order.getTenantId(),
                order.getOrderNumber(),
                order.getPublicToken(),
                order.getCustomerId(),
                customer != null ? customer.name() : null,
                customer != null ? customer.phone() : null,
                order.getCreatedByUserId(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemResponses
        );
    }
}
