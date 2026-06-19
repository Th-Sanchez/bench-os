package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.BudgetItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetItemJpaRepository extends JpaRepository<BudgetItemEntity, UUID> {
    List<BudgetItemEntity> findAllByServiceOrderId(UUID serviceOrderId);
    void deleteAllByServiceOrderId(UUID serviceOrderId);
}
