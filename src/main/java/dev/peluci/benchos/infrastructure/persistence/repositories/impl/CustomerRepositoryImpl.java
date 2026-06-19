package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Customer;
import dev.peluci.benchos.domain.repositories.CustomerRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.CustomerMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.CustomerJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerMapper customerMapper;

    @Override
    public Customer save(Customer customer) {
        return customerMapper.toDomain(
            customerJpaRepository.save(customerMapper.toEntity(customer))
        );
    }

    @Override
    public Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId) {
        return customerJpaRepository
            .findByIdAndTenantId(id, tenantId)
            .map(customerMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByPhoneAndTenantId(
            String phone,
            UUID tenantId
    ) {
        return customerJpaRepository
            .findByPhoneAndTenantId(phone, tenantId)
            .map(customerMapper::toDomain);
    }

    @Override
    public List<Customer> findAllByTenantId(UUID tenantId) {
        return customerJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(customerMapper::toDomain)
            .toList();
    }
}
