package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.ItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionItemStatusRequest(
        @NotNull ItemStatus status,
        @Size(max = 1000) String cancellationReason
) {
}
