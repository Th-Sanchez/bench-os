package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.Voltage;
import com.benchos.api.order.internal.entity.IntakeChecklistEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateServiceOrderItemRequest(
        @NotBlank @Size(max = 80) String applianceType,
        @NotBlank @Size(max = 80) String brand,
        @Size(max = 120) String model,
        @NotNull Voltage voltage,
        @Size(max = 100) String serialNumber,
        @Size(max = 2000) String accessories,
        @NotBlank @Size(max = 2000) String reportedIssue,
        @Size(max = 30) List<@Valid IntakeChecklistEntry> intakeChecklist
) {
}
