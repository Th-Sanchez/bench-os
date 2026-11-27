package com.benchos.api.integration.internal.repository;

import com.benchos.api.integration.internal.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<WebhookEndpoint> findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(UUID tenantId);

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
