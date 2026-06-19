package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.TenantConfigEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantConfigJpaRepository extends JpaRepository<TenantConfigEntity, UUID> {
    Optional<TenantConfigEntity> findByTenantId(UUID tenantId);
}
