package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.ServiceOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository {
    ServiceOrder save(ServiceOrder serviceOrder);
    Optional<ServiceOrder> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ServiceOrder> findByOsCodeAndTenantId(
            String osCode,
            UUID tenantId
    );
    List<ServiceOrder> findAllByTenantId(UUID tenantId);
    List<ServiceOrder> findAllByCustomerIdAndTenantId(
            UUID customerId,
            UUID tenantId
    );
    int countByTenantIdAndReceivedAt(UUID tenantId, LocalDate receivedAt);
}
