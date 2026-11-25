package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.PartCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartCatalogRepository extends JpaRepository<PartCatalogItem, UUID> {

    List<PartCatalogItem> findAllByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    Optional<PartCatalogItem> findByIdAndTenantIdAndActiveTrue(UUID id, UUID tenantId);

    boolean existsByTenantIdAndActiveTrueAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndActiveTrueAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
