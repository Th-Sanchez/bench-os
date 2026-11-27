package com.benchos.api.integration.internal.web.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWebhookEndpointRequest(
        @Size(max = 120) String name,
        @Size(max = 500) String targetUrl,
        List<String> events,
        Boolean active
) {
}
