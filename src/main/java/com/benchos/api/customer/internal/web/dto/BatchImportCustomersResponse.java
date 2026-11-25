package com.benchos.api.customer.internal.web.dto;

import java.util.List;

public record BatchImportCustomersResponse(
        int importedCount,
        int duplicatesSkipped,
        int invalidSkipped,
        List<CustomerResponse> customers
) {
}
