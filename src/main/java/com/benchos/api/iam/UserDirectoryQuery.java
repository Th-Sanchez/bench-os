package com.benchos.api.iam;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public module API allowing other modules (e.g., order) to resolve team members
 * and commission defaults without coupling to IAM internals.
 */
public interface UserDirectoryQuery {

    Optional<UserSnapshot> findActiveById(UUID userId, UUID tenantId);

    Map<UUID, UserSnapshot> findAllByIds(Collection<UUID> userIds, UUID tenantId);

    List<UserSnapshot> listAssignableTechnicians(UUID tenantId);
}
