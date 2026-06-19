package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.CustomerEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CustomerEntity> findByPhoneAndTenantId(
            String phone,
            UUID tenantId
    );
    List<CustomerEntity> findAllByTenantId(UUID tenantId);
}
