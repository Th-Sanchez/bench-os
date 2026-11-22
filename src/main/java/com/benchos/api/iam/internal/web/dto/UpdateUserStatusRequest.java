package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull Boolean active
) {
}
