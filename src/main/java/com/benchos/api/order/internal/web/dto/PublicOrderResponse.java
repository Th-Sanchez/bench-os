package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.ServiceOrder;

import java.math.BigDecimal;
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
        String shopLogoDataUrl,
        String shopAddress,
        String businessHours,
        String warrantyTerms,
        String googleReviewUrl,
        BigDecimal pixAmount,
        String pixPayload,
        String pixQrSvg,
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
            String shopLogoDataUrl,
            String shopAddress,
            String businessHours,
            String warrantyTerms,
            String googleReviewUrl,
            BigDecimal pixAmount,
            String pixPayload,
            String pixQrSvg,
            Map<UUID, List<PublicOrderItemPhotoResponse>> photosByItemId,
            Map<UUID, List<String>> careTipsByItemId,
            Map<UUID, List<PublicQuoteOptionResponse>> quoteOptionsByItemId
    ) {
        List<PublicOrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> PublicOrderItemResponse.from(
                        item,
                        order.getOrderNumber(),
                        photosByItemId.getOrDefault(item.getId(), List.of()),
                        careTipsByItemId.getOrDefault(item.getId(), List.of()),
                        quoteOptionsByItemId.getOrDefault(item.getId(), List.of())
                ))
                .toList();
        return new PublicOrderResponse(
                shopName,
                shopPhone,
                shopLogoDataUrl,
                shopAddress,
                businessHours,
                warrantyTerms,
                googleReviewUrl,
                pixAmount,
                pixPayload,
                pixQrSvg,
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getCustomerRating(),
                order.getCustomerFeedback(),
                order.getCustomerRatedAt(),
                itemResponses
        );
    }
}
