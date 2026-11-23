package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.Voltage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateServiceOrderItemRequest(
        @NotBlank @Size(max = 80) String applianceType,
        @NotBlank @Size(max = 80) String brand,
        @Size(max = 120) String model,
        @NotNull Voltage voltage,
        @Size(max = 100) String serialNumber,
        @Size(max = 2000) String accessories,
        @NotBlank @Size(max = 2000) String reportedIssue
) {
}
