package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.ServiceOrderEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderJpaRepository extends JpaRepository<ServiceOrderEntity, UUID> {
    Optional<ServiceOrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ServiceOrderEntity> findByOsCodeAndTenantId(
            String osCode,
            UUID tenantId
    );
    List<ServiceOrderEntity> findAllByTenantId(UUID tenantId);
    List<ServiceOrderEntity> findAllByCustomerIdAndTenantId(
            UUID customerId,
            UUID tenantId
    );
    int countByTenantIdAndReceivedAt(UUID tenantId, LocalDate receivedAt);
}
