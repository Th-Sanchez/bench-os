package dev.peluci.benchos.infrastructure.persistence.repositories.impl;

import dev.peluci.benchos.domain.entities.BudgetItem;
import dev.peluci.benchos.domain.repositories.BudgetItemRepository;
import dev.peluci.benchos.infrastructure.persistence.mappers.BudgetItemMapper;
import dev.peluci.benchos.infrastructure.persistence.repositories.jpa.BudgetItemJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BudgetItemRepositoryImpl implements BudgetItemRepository {

    private final BudgetItemJpaRepository budgetItemJpaRepository;
    private final BudgetItemMapper budgetItemMapper;

    @Override
    public BudgetItem save(BudgetItem budgetItem) {
        return budgetItemMapper.toDomain(
            budgetItemJpaRepository.save(budgetItemMapper.toEntity(budgetItem))
        );
    }

    @Override
    public List<BudgetItem> saveAll(List<BudgetItem> budgetItems) {
        return budgetItemJpaRepository
            .saveAll(
                budgetItems.stream().map(budgetItemMapper::toEntity).toList()
            )
            .stream()
            .map(budgetItemMapper::toDomain)
            .toList();
    }

    @Override
    public List<BudgetItem> findAllByServiceOrderId(UUID serviceOrderId) {
        return budgetItemJpaRepository
            .findAllByServiceOrderId(serviceOrderId)
            .stream()
            .map(budgetItemMapper::toDomain)
            .toList();
    }

    @Override
    public void deleteAllByServiceOrderId(UUID serviceOrderId) {
        budgetItemJpaRepository.deleteAllByServiceOrderId(serviceOrderId);
    }
}
