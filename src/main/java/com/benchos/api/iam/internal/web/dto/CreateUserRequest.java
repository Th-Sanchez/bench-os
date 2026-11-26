package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPercent
) {
}
