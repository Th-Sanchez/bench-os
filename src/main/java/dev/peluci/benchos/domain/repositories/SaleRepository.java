package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Sale;
import java.util.List;
import java.util.UUID;

public interface SaleRepository {
    Sale save(Sale sale);
    List<Sale> findAllByTenantId(UUID tenantId);
    List<Sale> findAllByCustomerIdAndTenantId(UUID customerId, UUID tenantId);
}
