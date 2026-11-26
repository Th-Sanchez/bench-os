package com.benchos.api.iam;

import java.math.BigDecimal;
import java.util.UUID;

public record UserSnapshot(
        UUID id,
        String name,
        Role role,
        boolean active,
        BigDecimal commissionPercent
) {
}
