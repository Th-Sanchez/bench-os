package com.benchos.api.customer.internal.web.dto;

import com.benchos.api.customer.internal.entity.Customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID tenantId,
        String name,
        String phone,
        String document,
        String notes,
        Instant createdAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getName(),
                customer.getPhone(),
                customer.getDocument(),
                customer.getNotes(),
                customer.getCreatedAt()
        );
    }
}
