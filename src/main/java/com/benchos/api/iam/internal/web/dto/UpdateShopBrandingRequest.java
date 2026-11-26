package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateShopBrandingRequest(
        String shopLogoDataUrl,
        Boolean clearLogo,
        @Size(max = 300) String shopAddress,
        @Size(max = 200) String businessHours,
        @Size(max = 3000) String warrantyTerms
) {
}
