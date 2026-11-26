package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.QuoteOption;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteOptionResponse(
        UUID id,
        int optionIndex,
        String label,
        String description,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal partsCostActual,
        BigDecimal discount,
        BigDecimal totalPrice,
        BigDecimal estimatedNetProfit,
        int warrantyDays,
        boolean recommended
) {
    public static QuoteOptionResponse from(QuoteOption opt) {
        return new QuoteOptionResponse(
                opt.getId(),
                opt.getOptionIndex(),
                opt.getLabel(),
                opt.getDescription(),
                opt.getLaborCost(),
                opt.getPartsCost(),
                opt.getPartsCostActual(),
                opt.getDiscount(),
                opt.getTotalPrice(),
                opt.getEstimatedNetProfit(),
                opt.getWarrantyDays(),
                opt.isRecommended()
        );
    }
}
