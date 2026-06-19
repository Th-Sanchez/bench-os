package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Payment;
import dev.peluci.benchos.domain.repositories.PaymentRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.PaymentMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.PaymentJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public Payment save(Payment payment) {
        return paymentMapper.toDomain(
            paymentJpaRepository.save(paymentMapper.toEntity(payment))
        );
    }

    @Override
    public List<Payment> findAllByServiceOrderId(UUID serviceOrderId) {
        return paymentJpaRepository
            .findAllByServiceOrderId(serviceOrderId)
            .stream()
            .map(paymentMapper::toDomain)
            .toList();
    }

    @Override
    public List<Payment> findAllByTenantId(UUID tenantId) {
        return paymentJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(paymentMapper::toDomain)
            .toList();
    }
}
