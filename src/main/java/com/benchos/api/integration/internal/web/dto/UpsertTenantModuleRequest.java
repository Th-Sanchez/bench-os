package com.benchos.api.integration.internal.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpsertTenantModuleRequest(
        @NotNull Boolean enabled,
        Map<String, Object> config
) {
}
