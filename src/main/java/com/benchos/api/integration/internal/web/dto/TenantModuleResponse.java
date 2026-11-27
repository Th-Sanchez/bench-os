package com.benchos.api.integration.internal.web.dto;

import java.time.Instant;
import java.util.Map;

public record TenantModuleResponse(
        String moduleKey,
        String name,
        String description,
        boolean builtIn,
        boolean enabled,
        Map<String, Object> config,
        Instant updatedAt
) {
}
