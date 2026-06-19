package dev.peluci.benchos.domain.repositories;

import dev.peluci.benchos.domain.entities.BudgetItem;
import java.util.List;
import java.util.UUID;

public interface BudgetItemRepository {
    BudgetItem save(BudgetItem budgetItem);
    List<BudgetItem> saveAll(List<BudgetItem> budgetItems);
    List<BudgetItem> findAllByServiceOrderId(UUID serviceOrderId);
    void deleteAllByServiceOrderId(UUID serviceOrderId);
}
