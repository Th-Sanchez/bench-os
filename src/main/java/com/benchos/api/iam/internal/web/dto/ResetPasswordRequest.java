package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Verification code must be 6 digits") String code,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
