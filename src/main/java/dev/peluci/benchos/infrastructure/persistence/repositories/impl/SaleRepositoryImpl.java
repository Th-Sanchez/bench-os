package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Sale;
import dev.peluci.benchos.domain.repositories.SaleRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.SaleMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.SaleJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SaleRepositoryImpl implements SaleRepository {

    private final SaleJpaRepository saleJpaRepository;
    private final SaleMapper saleMapper;

    @Override
    public Sale save(Sale sale) {
        return saleMapper.toDomain(
            saleJpaRepository.save(saleMapper.toEntity(sale))
        );
    }

    @Override
    public List<Sale> findAllByTenantId(UUID tenantId) {
        return saleJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(saleMapper::toDomain)
            .toList();
    }

    @Override
    public List<Sale> findAllByCustomerIdAndTenantId(
        UUID customerId,
        UUID tenantId
    ) {
        return saleJpaRepository
            .findAllByCustomerIdAndTenantId(customerId, tenantId)
            .stream()
            .map(saleMapper::toDomain)
            .toList();
    }
}
