package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.DiscountEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountJpaRepository extends JpaRepository<DiscountEntity, UUID> {
    Optional<DiscountEntity> findByServiceOrderId(UUID serviceOrderId);
}
