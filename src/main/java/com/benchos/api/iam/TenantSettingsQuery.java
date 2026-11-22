package com.benchos.api.iam;

import java.util.UUID;

/**
 * Public module API allowing other modules (e.g., order) to read tenant settings without coupling
 * to IAM internal entities or repositories.
 */
public interface TenantSettingsQuery {

    TenantSettingsSnapshot getSettings(UUID tenantId);
}
