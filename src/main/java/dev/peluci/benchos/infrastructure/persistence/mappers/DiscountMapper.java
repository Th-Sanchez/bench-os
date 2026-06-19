package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Discount;
import dev.peluci.benchos.infrastructure.persistence.entities.DiscountEntity;
import org.springframework.stereotype.Component;

@Component
public class DiscountMapper {

    public Discount toDomain(DiscountEntity entity) {
        return new Discount(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getServiceOrderId(),
            entity.getRequestedAmount(),
            entity.getApprovedAmount(),
            entity.isApproved()
        );
    }

    public DiscountEntity toEntity(Discount domain) {
        return DiscountEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .serviceOrderId(domain.getServiceOrderId())
            .requestedAmount(domain.getRequestedAmount())
            .approvedAmount(domain.getApprovedAmount())
            .approved(domain.isApproved())
            .build();
    }
}
