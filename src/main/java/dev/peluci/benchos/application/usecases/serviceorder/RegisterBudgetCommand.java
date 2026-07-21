package dev.peluci.benchos.application.usecases.serviceorder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RegisterBudgetCommand
 */
public record RegisterBudgetCommand(
    UUID tenantId,
    UUID serviceOrderId,
    List<BudgetItemInput> itens
) {

    /**
     * BudgetItemInput
     */
    public record BudgetItemInput(
        String description,
        String type,
        BigDecimal unitPrice,
        Integer quantity
    ) {
    }

}
