package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Product> findAllByTenantId(UUID tenantId);
}
