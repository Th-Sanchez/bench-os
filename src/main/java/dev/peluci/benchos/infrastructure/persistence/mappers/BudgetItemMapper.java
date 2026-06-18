package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.BudgetItem;
import dev.peluci.benchos.infrastructure.persistence.entities.BudgetItemEntity;

public class BudgetItemMapper {

    public BudgetItem toDomain(BudgetItemEntity entity) {
        return new BudgetItem(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getServiceOrderId(),
            entity.getDescription(),
            entity.getType(),
            entity.getUnitPrice(),
            entity.getQuantity()
        );
    }

    public BudgetItemEntity toEntity(BudgetItem domain) {
        return BudgetItemEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .serviceOrderId(domain.getServiceOrderId())
            .description(domain.getDescription())
            .type(domain.getType())
            .unitPrice(domain.getUnitPrice())
            .quantity(domain.getQuantity())
            .build();
    }
}
