package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.QuoteOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteOptionRepository extends JpaRepository<QuoteOption, UUID> {

    List<QuoteOption> findAllByTenantIdAndServiceOrderItemIdOrderByOptionIndexAsc(
            UUID tenantId,
            UUID serviceOrderItemId
    );

    List<QuoteOption> findAllByTenantIdAndServiceOrderItemIdInOrderByServiceOrderItemIdAscOptionIndexAsc(
            UUID tenantId,
            Collection<UUID> serviceOrderItemIds
    );

    Optional<QuoteOption> findByIdAndTenantIdAndServiceOrderItemId(
            UUID id,
            UUID tenantId,
            UUID serviceOrderItemId
    );

    void deleteAllByTenantIdAndServiceOrderItemId(UUID tenantId, UUID serviceOrderItemId);
}
