package com.benchos.api.integration.internal.service;

import com.benchos.api.integration.internal.config.WebhookProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class JdkWebhookHttpSender implements WebhookHttpSender {

    private final WebhookProperties properties;
    private final HttpClient httpClient;

    public JdkWebhookHttpSender(WebhookProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public SendResult post(URI targetUri, String payloadJson, Map<String, String> headers) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(Duration.ofMillis(Math.max(1000, properties.getRequestTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "BenchOS-Webhooks/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson));

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            int durationMs = (int) ((System.nanoTime() - startNanos) / 1_000_000L);
            int code = response.statusCode();
            boolean ok = code >= 200 && code < 300;
            String body = response.body();
            return new SendResult(
                    ok,
                    code,
                    durationMs,
                    body,
                    ok ? null : "Endpoint responded with HTTP " + code
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            int durationMs = (int) ((System.nanoTime() - startNanos) / 1_000_000L);
            return new SendResult(false, null, durationMs, null, "Delivery interrupted");
        } catch (Exception ex) {
            int durationMs = (int) ((System.nanoTime() - startNanos) / 1_000_000L);
            String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    : ex.getClass().getSimpleName();
            return new SendResult(false, null, durationMs, null, msg);
        }
    }
}
