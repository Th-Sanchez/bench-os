package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.ServiceOrder;
import dev.peluci.benchos.domain.repositories.ServiceOrderRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.ServiceOrderMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.ServiceOrderJpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ServiceOrderRepositoryImpl implements ServiceOrderRepository {

    private final ServiceOrderJpaRepository serviceOrderJpaRepository;
    private final ServiceOrderMapper serviceOrderMapper;

    @Override
    public ServiceOrder save(ServiceOrder serviceOrder) {
        return serviceOrderMapper.toDomain(
            serviceOrderJpaRepository.save(
                serviceOrderMapper.toEntity(serviceOrder)
            )
        );
    }

    @Override
    public Optional<ServiceOrder> findByIdAndTenantId(UUID id, UUID tenantId) {
        return serviceOrderJpaRepository
            .findByIdAndTenantId(id, tenantId)
            .map(serviceOrderMapper::toDomain);
    }

    @Override
    public Optional<ServiceOrder> findByOsCodeAndTenantId(
        String osCode,
        UUID tenantId
    ) {
        return serviceOrderJpaRepository
            .findByOsCodeAndTenantId(osCode, tenantId)
            .map(serviceOrderMapper::toDomain);
    }

    @Override
    public List<ServiceOrder> findAllByTenantId(UUID tenantId) {
        return serviceOrderJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(serviceOrderMapper::toDomain)
            .toList();
    }

    @Override
    public List<ServiceOrder> findAllByCustomerIdAndTenantId(
        UUID customerId,
        UUID tenantId
    ) {
        return serviceOrderJpaRepository
            .findAllByCustomerIdAndTenantId(customerId, tenantId)
            .stream()
            .map(serviceOrderMapper::toDomain)
            .toList();
    }

    @Override
    public int countByTenantIdAndReceivedAt(
        UUID tenantId,
        LocalDate receivedAt
    ) {
        return serviceOrderJpaRepository.countByTenantIdAndReceivedAt(
            tenantId,
            receivedAt
        );
    }
}
