package com.benchos.api.integration.internal.service;

import java.net.URI;
import java.util.Map;

public interface WebhookHttpSender {

    SendResult post(URI targetUri, String payloadJson, Map<String, String> headers);

    record SendResult(
            boolean success,
            Integer httpStatus,
            int durationMs,
            String responseSnippet,
            String errorMessage
    ) {
    }
}
