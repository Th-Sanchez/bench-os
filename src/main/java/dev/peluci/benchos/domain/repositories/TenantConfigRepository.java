package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.TenantConfig;
import java.util.Optional;
import java.util.UUID;

public interface TenantConfigRepository {
    TenantConfig save(TenantConfig tenantConfig);
    Optional<TenantConfig> findByTenantId(UUID tenantId);
}
