package com.benchos.api.integration.internal.repository;

import com.benchos.api.integration.internal.entity.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findAllByTenantIdAndWebhookEndpointIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID webhookEndpointId,
            Pageable pageable
    );

    Optional<WebhookDelivery> findByIdAndTenantIdAndWebhookEndpointId(
            UUID id,
            UUID tenantId,
            UUID webhookEndpointId
    );
}
