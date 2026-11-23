package com.benchos.api.order.internal.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateServiceOrderRequest(
        @NotNull UUID customerId,
        @Size(max = 2000) String notes,
        @NotEmpty @Valid List<CreateServiceOrderItemRequest> items
) {
}
