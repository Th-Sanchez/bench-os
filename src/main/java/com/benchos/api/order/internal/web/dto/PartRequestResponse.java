package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.PartRequestStatus;
import com.benchos.api.order.internal.entity.PartRequest;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartRequestResponse(
        UUID id,
        UUID serviceOrderId,
        UUID serviceOrderItemId,
        long orderNumber,
        int itemIndex,
        String itemTag,
        ItemStatus itemStatus,
        String applianceDisplayName,
        String customerName,
        UUID partCatalogId,
        String partName,
        int quantity,
        BigDecimal estimatedUnitCost,
        BigDecimal estimatedTotalCost,
        BigDecimal actualUnitCost,
        BigDecimal effectiveTotalCost,
        String supplierName,
        String purchaseUrl,
        String notes,
        PartRequestStatus status,
        boolean expenseRecorded,
        Instant orderedAt,
        Instant arrivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PartRequestResponse from(PartRequest request, String customerName) {
        ServiceOrderItem item = request.getServiceOrderItem();
        ServiceOrder order = item.getServiceOrder();
        return new PartRequestResponse(
                request.getId(),
                order.getId(),
                item.getId(),
                order.getOrderNumber(),
                item.getItemIndex(),
                item.getTag(),
                item.getStatus(),
                item.getApplianceDisplayName(),
                customerName,
                request.getPartCatalogId(),
                request.getPartName(),
                request.getQuantity(),
                request.getEstimatedUnitCost(),
                request.getEstimatedTotalCost(),
                request.getActualUnitCost(),
                request.getEffectiveTotalCost(),
                request.getSupplierName(),
                request.getPurchaseUrl(),
                request.getNotes(),
                request.getStatus(),
                request.isExpenseRecorded(),
                request.getOrderedAt(),
                request.getArrivedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
