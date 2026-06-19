package dev.peluci.benchos.infrastructure.persistence.mappers;

import dev.peluci.benchos.domain.entities.Product;
import dev.peluci.benchos.infrastructure.persistence.entities.ProductEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toDomain(ProductEntity entity) {
        return new Product(
            entity.getId(),
            entity.isActive(),
            entity.getDtInsert(),
            entity.getDtUpdate(),
            entity.getDtDelete(),
            entity.getTenantId(),
            entity.getName(),
            entity.getCategory(),
            entity.getPrice()
        );
    }

    public ProductEntity toEntity(Product domain) {
        return ProductEntity.builder()
            .id(domain.getId())
            .active(domain.isActive())
            .dtInsert(domain.getDtInsert())
            .dtUpdate(domain.getDtUpdate())
            .dtDelete(domain.getDtDelete())
            .tenantId(domain.getTenantId())
            .name(domain.getName())
            .category(domain.getCategory())
            .price(domain.getPrice())
            .build();
    }
}
