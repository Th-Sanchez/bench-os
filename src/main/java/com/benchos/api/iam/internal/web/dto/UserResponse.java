package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.internal.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String name,
        String email,
        Role role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
