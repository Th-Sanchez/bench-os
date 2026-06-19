package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.Discount;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository {
    Discount save(Discount discount);
    Optional<Discount> findByServiceOrderId(UUID serviceOrderId);
}
