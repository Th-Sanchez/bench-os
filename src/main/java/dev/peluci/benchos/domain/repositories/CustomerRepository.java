package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    Customer save(Customer customer);
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Customer> findByPhoneAndTenantId(String phone, UUID tenantId);
    List<Customer> findAllByTenantId(UUID tenantId);
}
