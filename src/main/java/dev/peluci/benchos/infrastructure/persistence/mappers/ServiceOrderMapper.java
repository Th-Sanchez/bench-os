package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.ServiceOrder;
import dev.peluci.benchos.infrastructure.persistence.entities.ServiceOrderEntity;
import org.springframework.stereotype.Component;

@Component
public class ServiceOrderMapper {

    public ServiceOrder toDomain(ServiceOrderEntity entity) {
        return new ServiceOrder(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getCustomerId(),
            entity.getAttendantId(),
            entity.getOsCode(),
            entity.getStatus(),
            entity.getDeviceModel(),
            entity.getDeviceVoltage(),
            entity.getReceivedAt(),
            entity.getDeadlineAt(),
            entity.isDiscardAuthorized(),
            entity.getNotes()
        );
    }

    public ServiceOrderEntity toEntity(ServiceOrder domain) {
        return ServiceOrderEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .customerId(domain.getCustomerId())
            .attendantId(domain.getAttendantId())
            .osCode(domain.getOsCode())
            .status(domain.getStatus())
            .deviceModel(domain.getDeviceModel())
            .deviceVoltage(domain.getDeviceVoltage())
            .receivedAt(domain.getReceivedAt())
            .deadlineAt(domain.getDeadlineAt())
            .discardAuthorized(domain.isDiscardAuthorized())
            .notes(domain.getNotes())
            .build();
    }
}
