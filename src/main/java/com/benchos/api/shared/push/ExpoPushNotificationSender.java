package com.benchos.api.shared.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTPS client for the Expo Push API ({@code POST https://exp.host/--/api/v2/push/send}).
 * Uses the standard JDK {@link HttpClient} to keep container memory overhead minimal, splits batches
 * into Expo's 100-message maximum chunk size, and collects {@code DeviceNotRegistered} tokens for cleanup.
 */
public class ExpoPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushNotificationSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BATCH_SIZE = 100;

    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ExpoPushNotificationSender(PushProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()
        );
    }

    ExpoPushNotificationSender(PushProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public PushDeliveryResult sendBatch(List<PushMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return PushDeliveryResult.empty();
        }

        int totalSent = 0;
        int totalFailed = 0;
        List<String> invalidTokens = new ArrayList<>();

        for (int start = 0; start < messages.size(); start += MAX_BATCH_SIZE) {
            List<PushMessage> chunk = messages.subList(start, Math.min(start + MAX_BATCH_SIZE, messages.size()));
            PushDeliveryResult chunkResult = sendChunk(chunk);
            totalSent += chunkResult.sentCount();
            totalFailed += chunkResult.failedCount();
            invalidTokens.addAll(chunkResult.invalidTokens());
        }

        return new PushDeliveryResult(totalSent, totalFailed, invalidTokens);
    }

    private PushDeliveryResult sendChunk(List<PushMessage> chunk) {
        List<Map<String, Object>> payload = new ArrayList<>(chunk.size());
        for (PushMessage message : chunk) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("to", message.toToken());
            item.put("title", message.title());
            item.put("body", message.body());
            item.put("sound", "default");
            item.put("channelId", message.channelId());
            if (!message.data().isEmpty()) {
                item.put("data", message.data());
            }
            payload.add(item);
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.expoEndpointUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

            if (properties.expoAccessToken() != null && !properties.expoAccessToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + properties.expoAccessToken().trim());
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Expo Push API rejected batch status={} body={}", response.statusCode(), response.body());
                return new PushDeliveryResult(0, chunk.size(), List.of());
            }

            return parseResponseTickets(chunk, response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending push notifications via Expo", ex);
            return new PushDeliveryResult(0, chunk.size(), List.of());
        } catch (Exception ex) {
            log.error("Failed to dispatch push notification batch via Expo", ex);
            return new PushDeliveryResult(0, chunk.size(), List.of());
        }
    }

    private PushDeliveryResult parseResponseTickets(List<PushMessage> chunk, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                return new PushDeliveryResult(chunk.size(), 0, List.of());
            }

            int sent = 0;
            int failed = 0;
            List<String> invalidTokens = new ArrayList<>();

            for (int i = 0; i < dataNode.size() && i < chunk.size(); i++) {
                JsonNode ticket = dataNode.get(i);
                String status = ticket.path("status").asString("");
                if ("ok".equalsIgnoreCase(status)) {
                    sent++;
                } else {
                    failed++;
                    String errorCode = ticket.path("details").path("error").asString("");
                    if ("DeviceNotRegistered".equals(errorCode)) {
                        invalidTokens.add(chunk.get(i).toToken());
                    }
                    log.warn(
                            "Expo Push ticket error token={} error={} message={}",
                            chunk.get(i).toToken(),
                            errorCode,
                            ticket.path("message").asString("")
                    );
                }
            }

            return new PushDeliveryResult(sent, failed, invalidTokens);
        } catch (Exception ex) {
            log.warn("Unable to parse Expo Push response body cleanly", ex);
            return new PushDeliveryResult(chunk.size(), 0, List.of());
        }
    }
}
