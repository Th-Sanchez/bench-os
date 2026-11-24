package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetUserPasswordRequest(
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
