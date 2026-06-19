package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Sale;
import dev.peluci.benchos.infrastructure.persistence.entities.SaleEntity;
import org.springframework.stereotype.Component;

@Component
public class SaleMapper {

    public Sale toDomain(SaleEntity entity) {
        return new Sale(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getProductId(),
            entity.getCustomerId(),
            entity.getQuantity(),
            entity.getTotalPrice(),
            entity.getSoldAt()
        );
    }

    public SaleEntity toEntity(Sale domain) {
        return SaleEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .productId(domain.getProductId())
            .customerId(domain.getCustomerId())
            .quantity(domain.getQuantity())
            .totalPrice(domain.getTotalPrice())
            .soldAt(domain.getSoldAt())
            .build();
    }
}
