package com.benchos.api.iam.internal.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record UpdateCareTipsRequest(
        @NotNull Map<String, List<String>> templates
) {
}
