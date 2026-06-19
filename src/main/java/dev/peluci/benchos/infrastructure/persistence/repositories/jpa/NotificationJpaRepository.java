package dev.peluci.benchos.infrastructure.persistence.repositories.jpa;

import dev.peluci.benchos.infrastructure.persistence.entities.NotificationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findAllByUserIdAndTenantId(
            UUID userId,
            UUID tenantId
    );

    @Modifying
    @Query(
            "UPDATE NotificationEntity n SET n.read = true WHERE n.userId = :userId AND n.tenantId = :tenantId"
    )
    void markAllAsReadByUserIdAndTenantId(UUID userId, UUID tenantId);
}
