package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.internal.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String document,
        String phone,
        boolean active,
        Instant createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getDocument(),
                tenant.getPhone(),
                tenant.isActive(),
                tenant.getCreatedAt()
        );
    }
}
