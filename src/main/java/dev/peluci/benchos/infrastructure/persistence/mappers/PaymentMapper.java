package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Payment;
import dev.peluci.benchos.infrastructure.persistence.entities.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public Payment toDomain(PaymentEntity entity) {
        return new Payment(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getServiceOrderId(),
            entity.getMethod(),
            entity.getSource(),
            entity.getStatus(),
            entity.getAmount(),
            entity.getPaidAt()
        );
    }

    public PaymentEntity toEntity(Payment domain) {
        return PaymentEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .serviceOrderId(domain.getServiceOrderId())
            .method(domain.getMethod())
            .source(domain.getSource())
            .status(domain.getStatus())
            .amount(domain.getAmount())
            .paidAt(domain.getPaidAt())
            .build();
    }
}
