package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Payment;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    List<Payment> findAllByServiceOrderId(UUID serviceOrderId);
    List<Payment> findAllByTenantId(UUID tenantId);
}
