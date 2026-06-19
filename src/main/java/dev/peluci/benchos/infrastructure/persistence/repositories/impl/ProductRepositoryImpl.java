package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Product;
import dev.peluci.benchos.domain.repositories.ProductRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.ProductMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.ProductJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;

    @Override
    public Product save(Product product) {
        return productMapper.toDomain(
            productJpaRepository.save(productMapper.toEntity(product))
        );
    }

    @Override
    public Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId) {
        return productJpaRepository
            .findByIdAndTenantId(id, tenantId)
            .map(productMapper::toDomain);
    }

    @Override
    public List<Product> findAllByTenantId(UUID tenantId) {
        return productJpaRepository
            .findAllByTenantId(tenantId)
            .stream()
            .map(productMapper::toDomain)
            .toList();
    }
}
