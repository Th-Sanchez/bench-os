package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Notification;
import dev.peluci.benchos.infrastructure.persistence.entities.NotificationEntity;

public class NotificationMapper {

    public Notification toDomain(NotificationEntity entity) {
        return new Notification(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getUserId(),
            entity.getMessage(),
            entity.isRead()
        );
    }

    public NotificationEntity toEntity(Notification domain) {
        return NotificationEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .userId(domain.getUserId())
            .message(domain.getMessage())
            .read(domain.isRead())
            .build();
    }
}
