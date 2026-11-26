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
        String shopPhone,
        String googleReviewUrl,
        long orderNumber,
        Instant createdAt,
        Integer customerRating,
        String customerFeedback,
        Instant customerRatedAt,
        List<PublicOrderItemResponse> items
) {
    public static PublicOrderResponse from(
            ServiceOrder order,
            String shopName,
            String shopPhone,
            String googleReviewUrl,
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
                shopPhone,
                googleReviewUrl,
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getCustomerRating(),
                order.getCustomerFeedback(),
                order.getCustomerRatedAt(),
                itemResponses
        );
    }
}
