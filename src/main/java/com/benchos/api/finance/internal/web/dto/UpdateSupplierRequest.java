package com.benchos.api.finance.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 30) String phone,
        @Size(max = 2000) String notes
) {
}
