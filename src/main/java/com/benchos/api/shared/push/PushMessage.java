package com.benchos.api.shared.push;

import java.util.Map;

/**
 * Outbound push notification payload targeting a single Expo Push Token.
 */
public record PushMessage(
        String toToken,
        String title,
        String body,
        Map<String, Object> data,
        String channelId
) {
    public static final String DEFAULT_CHANNEL_ID = "benchos-alerts";

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
        channelId = (channelId == null || channelId.isBlank()) ? DEFAULT_CHANNEL_ID : channelId;
    }

    public static PushMessage of(String toToken, String title, String body, Map<String, Object> data) {
        return new PushMessage(toToken, title, body, data, DEFAULT_CHANNEL_ID);
    }
}
