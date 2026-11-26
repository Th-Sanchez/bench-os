package com.benchos.api.order.internal.web.dto;

import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        Integer customerRating,
        String customerFeedback,
        Instant customerRatedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ServiceOrderItemResponse> items
) {
    public record ParentItemRef(UUID orderId, String tag) {
    }

    public static ServiceOrderResponse from(
            ServiceOrder order,
            CustomerSnapshot customer,
            List<ServiceOrderItem> items
    ) {
        return from(order, customer, items, Map.of());
    }

    public static ServiceOrderResponse from(
            ServiceOrder order,
            CustomerSnapshot customer,
            List<ServiceOrderItem> items,
            Map<UUID, ParentItemRef> parentRefsByItemId
    ) {
        List<ServiceOrderItemResponse> itemResponses = items.stream()
                .map(item -> {
                    ParentItemRef parentRef = item.getParentItemId() != null
                            ? parentRefsByItemId.get(item.getParentItemId())
                            : null;
                    return ServiceOrderItemResponse.from(
                            item,
                            order.getOrderNumber(),
                            parentRef != null ? parentRef.orderId() : null,
                            parentRef != null ? parentRef.tag() : null
                    );
                })
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
                order.getCustomerRating(),
                order.getCustomerFeedback(),
                order.getCustomerRatedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemResponses
        );
    }
}
