package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);
    void markAllAsReadByUserIdAndTenantId(UUID userId, UUID tenantId);
}
