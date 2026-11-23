package com.benchos.api.finance.internal.repository;

import com.benchos.api.finance.internal.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<Supplier> findAllByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndDeletedAtIsNullAndIdNot(UUID tenantId, String name, UUID id);

    List<Supplier> findAllByTenantIdAndDeletedAtIsNullOrderByNameAscCreatedAtDesc(UUID tenantId);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.tenantId = :tenantId
              AND s.deletedAt IS NULL
              AND LOWER(s.name) LIKE LOWER(CONCAT('%', :term, '%'))
            ORDER BY s.name ASC, s.createdAt DESC
            """)
    List<Supplier> searchActiveByName(@Param("tenantId") UUID tenantId, @Param("term") String term);
}
