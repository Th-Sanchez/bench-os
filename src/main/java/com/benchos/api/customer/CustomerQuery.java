package com.benchos.api.customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Public module API allowing other modules (e.g., order) to query active customers
 * without coupling to customer internal entities or repositories.
 */
public interface CustomerQuery {

    Optional<CustomerSnapshot> findActiveById(UUID customerId, UUID tenantId);
}
