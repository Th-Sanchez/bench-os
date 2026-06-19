package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Customer;
import dev.peluci.benchos.infrastructure.persistence.entities.CustomerEntity;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toDomain(CustomerEntity entity) {
        return new Customer(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getName(),
            entity.getPhone(),
            entity.getEmail()
        );
    }

    public CustomerEntity toEntity(Customer customer) {
        return CustomerEntity.builder()
            .id(customer.getId())
            .active(customer.isActive())
            .dtInsert(customer.getDtInsert())
            .dtUpdate(customer.getDtUpdate())
            .dtDelete(customer.getDtDelete())
            .tenantId(customer.getTenantId())
            .name(customer.getName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .build();
    }
}
