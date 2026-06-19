package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.TenantConfig;
import dev.peluci.benchos.domain.repositories.TenantConfigRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.TenantConfigMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.TenantConfigJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantConfigRepositoryImpl implements TenantConfigRepository {

    private final TenantConfigJpaRepository tenantConfigJpaRepository;
    private final TenantConfigMapper tenantConfigMapper;

    @Override
    public TenantConfig save(TenantConfig tenantConfig) {
        return tenantConfigMapper.toDomain(
            tenantConfigJpaRepository.save(
                tenantConfigMapper.toEntity(tenantConfig)
            )
        );
    }

    @Override
    public Optional<TenantConfig> findByTenantId(UUID tenantId) {
        return tenantConfigJpaRepository
            .findByTenantId(tenantId)
            .map(tenantConfigMapper::toDomain);
    }
}
