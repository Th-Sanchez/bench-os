package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Notification;
import dev.peluci.benchos.domain.repositories.NotificationRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.NotificationMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.NotificationJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;
    private final NotificationMapper notificationMapper;

    @Override
    public Notification save(Notification notification) {
        return notificationMapper.toDomain(
            notificationJpaRepository.save(
                notificationMapper.toEntity(notification)
            )
        );
    }

    @Override
    public List<Notification> findAllByUserIdAndTenantId(
        UUID userId,
        UUID tenantId
    ) {
        return notificationJpaRepository
            .findAllByUserIdAndTenantId(userId, tenantId)
            .stream()
            .map(notificationMapper::toDomain)
            .toList();
    }

    @Override
    public void markAllAsReadByUserIdAndTenantId(UUID userId, UUID tenantId) {
        notificationJpaRepository.markAllAsReadByUserIdAndTenantId(
            userId,
            tenantId
        );
    }
}
