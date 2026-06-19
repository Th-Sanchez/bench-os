package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Tenant;
import dev.peluci.benchos.domain.repositories.TenantRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.TenantMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.TenantJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantJpaRepository tenantJpaRepository;
    private final TenantMapper tenantMapper;

    @Override
    public Tenant save(Tenant tenant) {
        return tenantMapper.toDomain(
            tenantJpaRepository.save(tenantMapper.toEntity(tenant))
        );
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return tenantJpaRepository.findById(id).map(tenantMapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByCnpj(String cnpj) {
        return tenantJpaRepository.findByCnpj(cnpj).map(tenantMapper::toDomain);
    }
}
