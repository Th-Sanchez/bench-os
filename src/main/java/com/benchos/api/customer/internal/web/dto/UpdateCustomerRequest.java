package com.benchos.api.customer.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 30) String phone,
        @Size(max = 30) String document,
        @Size(max = 2000) String notes
) {
}
