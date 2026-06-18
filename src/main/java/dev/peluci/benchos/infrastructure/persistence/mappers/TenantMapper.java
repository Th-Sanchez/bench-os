package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Tenant;
import dev.peluci.benchos.infrastructure.persistence.entities.TenantEntity;

public class TenantMapper {

    public Tenant toDomain(TenantEntity entity) {
        return new Tenant(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTradeName(),
            entity.getCompanyName(),
            entity.getCnpj(),
            entity.getPhone(),
            entity.getStreet(),
            entity.getNumber(),
            entity.getCity(),
            entity.getState(),
            entity.getZipCode()
        );
    }

    public TenantEntity toEntity(Tenant domain) {
        return TenantEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tradeName(domain.getTradeName())
            .companyName(domain.getCompanyName())
            .cnpj(domain.getCnpj())
            .phone(domain.getPhone())
            .street(domain.getStreet())
            .number(domain.getNumber())
            .city(domain.getCity())
            .state(domain.getState())
            .zipCode(domain.getZipCode())
            .build();
    }
}
