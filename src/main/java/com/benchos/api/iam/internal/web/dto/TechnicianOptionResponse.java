package com.benchos.api.iam.internal.web.dto;

import com.benchos.api.iam.Role;
import com.benchos.api.iam.UserSnapshot;

import java.util.UUID;

public record TechnicianOptionResponse(
        UUID id,
        String name,
        Role role
) {
    public static TechnicianOptionResponse from(UserSnapshot snapshot) {
        return new TechnicianOptionResponse(snapshot.id(), snapshot.name(), snapshot.role());
    }
}
