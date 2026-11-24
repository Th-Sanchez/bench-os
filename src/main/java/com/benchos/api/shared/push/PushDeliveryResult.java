package com.benchos.api.shared.push;

import java.util.List;

/**
 * Aggregated delivery outcome of a push notification batch, including tokens reported
 * by Expo as {@code DeviceNotRegistered} so callers can prune stale records.
 */
public record PushDeliveryResult(
        int sentCount,
        int failedCount,
        List<String> invalidTokens
) {
    public PushDeliveryResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public static PushDeliveryResult empty() {
        return new PushDeliveryResult(0, 0, List.of());
    }
}
