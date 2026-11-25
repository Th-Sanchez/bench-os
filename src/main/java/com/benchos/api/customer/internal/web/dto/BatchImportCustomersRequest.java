package com.benchos.api.customer.internal.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchImportCustomersRequest(
        @NotEmpty @Size(max = 200) @Valid List<CreateCustomerRequest> customers
) {
}
