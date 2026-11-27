package com.benchos.api.integration.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWebhookEndpointRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 500) String targetUrl,
        @NotEmpty List<String> events,
        Boolean active
) {
}
