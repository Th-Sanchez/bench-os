package com.benchos.api.iam;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public module API allowing operational and financial modules to resolve active Expo push tokens
 * for a tenant (or specific roles) and prune tokens reported as unregistered by Expo.
 */
public interface PushTokenQuery {

    List<String> findActiveTokensForTenant(UUID tenantId);

    List<String> findActiveTokensForTenantRoles(UUID tenantId, Collection<Role> roles);

    void revokeInvalidTokens(Collection<String> invalidTokens);
}
