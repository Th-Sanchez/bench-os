package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushTokenRequest(
        @NotBlank(message = "Expo push token is required")
        @Size(max = 255, message = "Expo push token must be at most 255 characters")
        String expoPushToken,

        @NotNull(message = "Device platform is required")
        DevicePlatform platform,

        @Size(max = 120, message = "Device name must be at most 120 characters")
        String deviceName
) {
}
