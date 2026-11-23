package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.ServiceOrder;

import java.time.Instant;
import java.util.List;

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
    public static PublicOrderResponse from(ServiceOrder order, String shopName) {
        List<PublicOrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> PublicOrderItemResponse.from(item, order.getOrderNumber()))
                .toList();
        return new PublicOrderResponse(
                shopName,
                order.getOrderNumber(),
                order.getCreatedAt(),
                itemResponses
        );
    }
}
