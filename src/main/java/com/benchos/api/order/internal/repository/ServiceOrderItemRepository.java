package com.benchos.api.order.internal.repository;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID>, JpaSpecificationExecutor<ServiceOrderItem> {

    @EntityGraph(attributePaths = "serviceOrder")
    Optional<ServiceOrderItem> findByIdAndServiceOrderIdAndTenantId(UUID id, UUID serviceOrderId, UUID tenantId);

    List<ServiceOrderItem> findAllByTenantIdAndServiceOrderIdInOrderByServiceOrderIdAscItemIndexAsc(
            UUID tenantId,
            Collection<UUID> serviceOrderIds
    );

    @Override
    @EntityGraph(attributePaths = "serviceOrder")
    Page<ServiceOrderItem> findAll(Specification<ServiceOrderItem> spec, Pageable pageable);

    @Query("""
            SELECT i FROM ServiceOrderItem i
            WHERE i.status IN :statuses
              AND i.estimatedCompletionDate IS NOT NULL
              AND i.estimatedCompletionDate <= :cutoffDate
            ORDER BY i.estimatedCompletionDate ASC
            """)
    List<ServiceOrderItem> findAllByStatusInAndEstimatedCompletionDateOnOrBefore(
            @Param("statuses") Collection<ItemStatus> statuses,
            @Param("cutoffDate") LocalDate cutoffDate
    );
}
