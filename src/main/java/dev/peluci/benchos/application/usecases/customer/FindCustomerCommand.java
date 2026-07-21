package dev.peluci.benchos.application.usecases.customer;

import java.util.UUID;

/**
 * FindCustomerCommand
 */
public record FindCustomerCommand(
    UUID tenantId,
    String searchQuery // Verificar o tipo desse campo
) {
}
