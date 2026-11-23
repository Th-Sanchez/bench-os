package com.benchos.api.order.internal.web.dto;

import com.benchos.api.order.PhotoStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PresignPhotoUploadRequest(
        @NotNull PhotoStage stage,
        @NotBlank @Size(max = 50) String contentType
) {
}
