package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Tenant;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    Tenant save(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    Optional<Tenant> findByCnpj(String cnpj);
}
