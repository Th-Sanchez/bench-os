package dev.peluci.benchos.application.usecases.serviceorder;

import dev.peluci.benchos.domain.entities.ServiceOrder;

/**
 * RegisterBudgetUseCase
 *
 * Adiciona peças e mão de obra (BudgetItem), calcula o valor total e avança o status da OS para BUDGETED.
 */
public interface RegisterBudgetUseCase {

    void execute(RegisterBudgetCommand command);

}
