package com.benchos.api.order.internal.repository;

import com.benchos.api.order.internal.entity.TenantSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantSequenceRepository extends JpaRepository<TenantSequence, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO tenant_sequences (tenant_id, last_order_number)
            VALUES (:tenantId, 1000)
            ON CONFLICT (tenant_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TenantSequence s WHERE s.tenantId = :tenantId")
    Optional<TenantSequence> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
