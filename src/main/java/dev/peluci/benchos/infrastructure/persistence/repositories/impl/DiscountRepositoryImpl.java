package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.Discount;
import dev.peluci.benchos.domain.repositories.DiscountRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.DiscountMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.DiscountJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DiscountRepositoryImpl implements DiscountRepository {

    private final DiscountJpaRepository discountJpaRepository;
    private final DiscountMapper discountMapper;

    @Override
    public Discount save(Discount discount) {
        return discountMapper.toDomain(
            discountJpaRepository.save(discountMapper.toEntity(discount))
        );
    }

    @Override
    public Optional<Discount> findByServiceOrderId(UUID serviceOrderId) {
        return discountJpaRepository
            .findByServiceOrderId(serviceOrderId)
            .map(discountMapper::toDomain);
    }
}
