package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.ServiceOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sanitized customer-facing view of a service order accessed via public_token.
 * Intentionally omits internal shop data and customer PII.
 */
public record PublicOrderResponse(
        String shopName,
        long orderNumber,
        Instant createdAt,
        List<PublicOrderItemResponse> items
) {
    public static PublicOrderResponse from(
            ServiceOrder order,
            String shopName,
            Map<UUID, List<PublicOrderItemPhotoResponse>> photosByItemId
    ) {
        List<PublicOrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> PublicOrderItemResponse.from(
                        item,
                        order.getOrderNumber(),
                        photosByItemId.getOrDefault(item.getId(), List.of())
                ))
                .toList();
        return new PublicOrderResponse(
                shopName,
                order.getOrderNumber(),
                order.getCreatedAt(),
                itemResponses
        );
    }
}
