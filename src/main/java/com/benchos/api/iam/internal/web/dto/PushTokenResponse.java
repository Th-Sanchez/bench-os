package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.DevicePlatform;
import com.benchos.api.iam.internal.entity.UserPushToken;

import java.time.Instant;
import java.util.UUID;

public record PushTokenResponse(
        UUID id,
        String expoPushToken,
        DevicePlatform platform,
        String deviceName,
        Instant updatedAt
) {
    public static PushTokenResponse from(UserPushToken token) {
        return new PushTokenResponse(
                token.getId(),
                token.getExpoPushToken(),
                token.getPlatform(),
                token.getDeviceName(),
                token.getUpdatedAt()
        );
    }
}
