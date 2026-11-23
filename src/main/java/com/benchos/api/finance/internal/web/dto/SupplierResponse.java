package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.internal.entity.Supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        UUID tenantId,
        String name,
        String phone,
        String notes,
        Instant createdAt
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getTenantId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getNotes(),
                supplier.getCreatedAt()
        );
    }
}
