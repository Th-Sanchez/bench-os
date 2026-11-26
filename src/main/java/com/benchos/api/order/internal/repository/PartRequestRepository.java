package com.benchos.api.order.internal.repository;

import com.benchos.api.order.PartRequestStatus;
import com.benchos.api.order.internal.entity.PartRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartRequestRepository
        extends JpaRepository<PartRequest, UUID>, JpaSpecificationExecutor<PartRequest> {

    @EntityGraph(attributePaths = {"serviceOrderItem", "serviceOrderItem.serviceOrder"})
    Optional<PartRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = {"serviceOrderItem", "serviceOrderItem.serviceOrder"})
    List<PartRequest> findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(
            UUID tenantId,
            UUID serviceOrderItemId
    );

    long countByTenantIdAndServiceOrderItemIdAndStatusIn(
            UUID tenantId,
            UUID serviceOrderItemId,
            Collection<PartRequestStatus> statuses
    );

    List<PartRequest> findAllByTenantId(UUID tenantId);

    @Override
    @EntityGraph(attributePaths = {"serviceOrderItem", "serviceOrderItem.serviceOrder"})
    Page<PartRequest> findAll(Specification<PartRequest> spec, Pageable pageable);
}
