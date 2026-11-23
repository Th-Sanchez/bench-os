package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.ServiceOrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID>, JpaSpecificationExecutor<ServiceOrderItem> {

    List<ServiceOrderItem> findAllByTenantIdAndServiceOrderIdInOrderByServiceOrderIdAscItemIndexAsc(
            UUID tenantId,
            Collection<UUID> serviceOrderIds
    );

    @Override
    @EntityGraph(attributePaths = "serviceOrder")
    Page<ServiceOrderItem> findAll(Specification<ServiceOrderItem> spec, Pageable pageable);
}
