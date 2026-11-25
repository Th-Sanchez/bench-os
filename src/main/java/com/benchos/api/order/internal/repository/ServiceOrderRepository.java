package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.ServiceOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID>, JpaSpecificationExecutor<ServiceOrder> {

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByPublicToken(UUID publicToken);

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByPublicTokenAndTenantId(UUID publicToken, UUID tenantId);
}
