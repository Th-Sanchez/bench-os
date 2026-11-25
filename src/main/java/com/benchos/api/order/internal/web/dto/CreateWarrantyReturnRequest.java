package com.benchos.api.order.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarrantyReturnRequest(
        @NotBlank @Size(max = 2000) String reportedIssue,
        @Size(max = 1000) String accessories,
        @Size(max = 2000) String notes
) {
}
