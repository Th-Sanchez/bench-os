package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.PaymentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findAllByServiceOrderId(UUID serviceOrderId);
    List<PaymentEntity> findAllByTenantId(UUID tenantId);
}
