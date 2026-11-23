package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddApplianceTypeRequest(
        @NotBlank @Size(max = 80) String name
) {
}
