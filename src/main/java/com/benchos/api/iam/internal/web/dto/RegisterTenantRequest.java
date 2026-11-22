package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
        @NotBlank @Size(max = 120) String shopName,
        @NotBlank @Size(max = 30) String document,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
