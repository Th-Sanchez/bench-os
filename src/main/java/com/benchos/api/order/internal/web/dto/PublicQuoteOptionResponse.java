package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.QuoteOption;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized customer-facing quote option (never exposes internal partsCostActual or profit).
 */
public record PublicQuoteOptionResponse(
        UUID id,
        int optionIndex,
        String label,
        String description,
        BigDecimal totalPrice,
        int warrantyDays,
        boolean recommended
) {
    public static PublicQuoteOptionResponse from(QuoteOption opt) {
        return new PublicQuoteOptionResponse(
                opt.getId(),
                opt.getOptionIndex(),
                opt.getLabel(),
                opt.getDescription(),
                opt.getTotalPrice(),
                opt.getWarrantyDays(),
                opt.isRecommended()
        );
    }
}
