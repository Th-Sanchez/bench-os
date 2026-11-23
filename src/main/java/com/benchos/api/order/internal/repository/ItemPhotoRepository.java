package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.ItemPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemPhotoRepository extends JpaRepository<ItemPhoto, UUID> {

    List<ItemPhoto> findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(UUID tenantId, UUID serviceOrderItemId);

    List<ItemPhoto> findAllByTenantIdAndServiceOrderItemIdInOrderByCreatedAtAsc(
            UUID tenantId,
            Collection<UUID> serviceOrderItemIds
    );

    Optional<ItemPhoto> findByIdAndServiceOrderItemIdAndTenantId(UUID id, UUID serviceOrderItemId, UUID tenantId);

    boolean existsByStorageKey(String storageKey);

    long countByTenantIdAndServiceOrderItemId(UUID tenantId, UUID serviceOrderItemId);
}
