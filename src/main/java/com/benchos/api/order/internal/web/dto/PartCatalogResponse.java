package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.internal.entity.PartCatalogItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record PartCatalogResponse(
        UUID id,
        String name,
        String applianceCategory,
        BigDecimal averageCost,
        BigDecimal suggestedPrice,
        BigDecimal unitProfit,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static PartCatalogResponse from(PartCatalogItem entity) {
        BigDecimal profit = entity.getSuggestedPrice()
                .subtract(entity.getAverageCost())
                .setScale(2, RoundingMode.HALF_UP);
        return new PartCatalogResponse(
                entity.getId(),
                entity.getName(),
                entity.getApplianceCategory(),
                entity.getAverageCost(),
                entity.getSuggestedPrice(),
                profit,
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
