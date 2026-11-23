package com.benchos.api.customer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public module API allowing other modules (e.g., order) to query customers
 * without coupling to customer internal entities or repositories.
 */
public interface CustomerQuery {

    Optional<CustomerSnapshot> findActiveById(UUID customerId, UUID tenantId);

    Map<UUID, CustomerSnapshot> findAllByIds(Collection<UUID> customerIds, UUID tenantId);
}
