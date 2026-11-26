package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.ServiceOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID>, JpaSpecificationExecutor<ServiceOrder> {

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByPublicToken(UUID publicToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceOrder> findForUpdateByPublicToken(UUID publicToken);

    @EntityGraph(attributePaths = "items")
    Optional<ServiceOrder> findWithItemsByPublicTokenAndTenantId(UUID publicToken, UUID tenantId);
}
