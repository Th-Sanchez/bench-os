package com.benchos.api.customer;

import java.util.UUID;

/**
 * Public read-only snapshot of an active customer exposed to other application modules.
 */
public record CustomerSnapshot(
        UUID id,
        UUID tenantId,
        String name,
        String phone
) {
}
