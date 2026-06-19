package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.ProductEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {
    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<ProductEntity> findAllByTenantId(UUID tenantId);
}
