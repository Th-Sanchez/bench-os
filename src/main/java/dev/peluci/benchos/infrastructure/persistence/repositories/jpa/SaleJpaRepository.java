package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.SaleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, UUID> {
    List<SaleEntity> findAllByTenantId(UUID tenantId);
    List<SaleEntity> findAllByCustomerIdAndTenantId(
            UUID customerId,
            UUID tenantId
    );
}
