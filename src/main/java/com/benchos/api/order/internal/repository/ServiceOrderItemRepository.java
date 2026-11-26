package com.benchos.api.order.internal.repository;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, UUID>, JpaSpecificationExecutor<ServiceOrderItem> {

    @EntityGraph(attributePaths = "serviceOrder")
    Optional<ServiceOrderItem> findByIdAndServiceOrderIdAndTenantId(UUID id, UUID serviceOrderId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM ServiceOrderItem i
            WHERE i.id = :itemId
              AND i.serviceOrder.id = :orderId
              AND i.tenantId = :tenantId
            """)
    Optional<ServiceOrderItem> findForUpdateByIdAndServiceOrderIdAndTenantId(
            @Param("itemId") UUID itemId,
            @Param("orderId") UUID orderId,
            @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM ServiceOrderItem i
            WHERE i.serviceOrder.id = (SELECT o.id FROM ServiceOrder o WHERE o.publicToken = :publicToken)
              AND i.itemIndex = :itemIndex
            """)
    Optional<ServiceOrderItem> findForUpdateByPublicTokenAndItemIndex(
            @Param("publicToken") UUID publicToken,
            @Param("itemIndex") int itemIndex
    );

    @EntityGraph(attributePaths = "serviceOrder")
    List<ServiceOrderItem> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

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

    @Query("""
            SELECT i FROM ServiceOrderItem i
            WHERE i.tenantId = :tenantId
              AND i.status = com.benchos.api.order.ItemStatus.DELIVERED
              AND i.deliveredAt >= :startInclusive
              AND i.deliveredAt < :endExclusive
            """)
    List<ServiceOrderItem> findDeliveredInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );

    @Query("""
            SELECT i FROM ServiceOrderItem i
            WHERE i.tenantId = :tenantId
              AND (
                    (i.createdAt >= :startInclusive AND i.createdAt < :endExclusive)
                 OR (i.quoteSentAt >= :startInclusive AND i.quoteSentAt < :endExclusive)
                 OR (i.approvedAt >= :startInclusive AND i.approvedAt < :endExclusive)
                 OR (i.rejectedAt >= :startInclusive AND i.rejectedAt < :endExclusive)
                 OR (i.finishedAt >= :startInclusive AND i.finishedAt < :endExclusive)
                 OR (i.deliveredAt >= :startInclusive AND i.deliveredAt < :endExclusive)
                 OR (i.status IN (com.benchos.api.order.ItemStatus.QUOTE_SENT, com.benchos.api.order.ItemStatus.REJECTED)
                     AND i.updatedAt >= :startInclusive AND i.updatedAt < :endExclusive)
              )
            """)
    List<ServiceOrderItem> findForAnalyticsPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );

    List<ServiceOrderItem> findAllByTenantIdAndStatusIn(
            UUID tenantId,
            Collection<ItemStatus> statuses
    );
}
